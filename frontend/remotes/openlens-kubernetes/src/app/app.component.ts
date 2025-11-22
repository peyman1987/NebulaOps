import {
  Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef, signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule }  from '@angular/forms';
import {
  ClusterSnapshot, K8sPod, K8sDeployment, K8sService, K8sReplicaSet,
  mockCluster, statusClass, generatePodYaml, generateDeploymentYaml,
  generateServiceYaml, podLogs
} from './k8s-data';

type Section =
  'overview' | 'pods' | 'deployments' | 'services' | 'replicasets' |
  'statefulsets' | 'configmaps' | 'secrets' | 'nodes' | 'events' | 'helm';

type DetailTab = 'overview' | 'related' | 'yaml' | 'logs';

interface ScaleModal  { dep: K8sDeployment; value: number; }
interface ConfirmModal { title: string; message: string; action: () => void; }
interface Toast        { msg: string; type: 'ok' | 'err'; }

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit {
  cluster!: ClusterSnapshot;

  activeSection = signal<Section>('overview');
  activeNs      = signal('all');
  searchText    = signal('');
  selectedItem  = signal<Record<string, unknown> | null>(null);
  selectedKind  = signal('');
  activeTab     = signal<DetailTab>('overview');
  scaleModal    = signal<ScaleModal   | null>(null);
  confirmModal  = signal<ConfirmModal | null>(null);
  toast         = signal<Toast        | null>(null);
  yamlSaved     = signal<Record<string, string>>({});
  editorYaml    = signal('');

  constructor(private cdr: ChangeDetectorRef) {}

  ngOnInit(): void { this.cluster = mockCluster(); }

  sc(s: string): string { return statusClass(s); }

  filterNs<T extends { namespace: string }>(arr: T[]): T[] {
    const ns = this.activeNs();
    const q  = this.searchText().toLowerCase();
    let out = ns === 'all' ? arr : arr.filter(i => i.namespace === ns);
    if (q) out = out.filter(i => JSON.stringify(i).toLowerCase().includes(q));
    return out;
  }

  navigate(section: Section, item?: Record<string, unknown>, kind?: string): void {
    this.activeSection.set(section);
    this.selectedItem.set(item ?? null);
    this.selectedKind.set(kind ?? '');
    this.activeTab.set('overview');
    this.searchText.set('');
    if (item) this.editorYaml.set(this.buildYaml(item, kind ?? ''));
    this.cdr.markForCheck();
  }

  goBack(): void { this.navigate(this.activeSection()); }

  selectItem(item: Record<string, unknown>, kind: string): void {
    this.selectedItem.set(item);
    this.selectedKind.set(kind);
    this.activeTab.set('overview');
    this.editorYaml.set(this.buildYaml(item, kind));
    this.cdr.markForCheck();
  }

  navToRelated(section: Section, name: string, namespace?: string): void {
    const data = (this.cluster as unknown as Record<string, unknown[]>)[section] as
      Array<Record<string, unknown>> | undefined;
    if (!data) return;
    const item = data.find(i => i['name'] === name && (!namespace || i['namespace'] === namespace));
    if (item) {
      if (namespace) this.activeNs.set(namespace);
      this.navigate(section, item, this.kindOf(section));
    }
  }

  kindOf(s: Section): string {
    const m: Partial<Record<Section, string>> = {
      pods: 'Pod', deployments: 'Deployment', services: 'Service',
      replicasets: 'ReplicaSet', statefulsets: 'StatefulSet',
    };
    return m[s] ?? s;
  }

  buildYaml(item: Record<string, unknown>, kind: string): string {
    if (kind === 'Pod')        return generatePodYaml(item as unknown as K8sPod);
    if (kind === 'Deployment') return generateDeploymentYaml(item as unknown as K8sDeployment);
    if (kind === 'Service')    return generateServiceYaml(item as unknown as K8sService);
    return `# ${kind}\n# name: ${item['name']}\n# namespace: ${item['namespace']}`;
  }

  relatedPods(item: Record<string, unknown>): K8sPod[] {
    const name = item['name'] as string;
    const ns   = item['namespace'] as string;
    const sel  = (item['selector'] ?? {}) as Record<string, string>;
    return this.cluster.pods.filter(p => {
      if (p.namespace !== ns) return false;
      if (item['replicas'] !== undefined) return p.deployment === name;
      if (Object.keys(sel).length) return Object.entries(sel).every(([k, v]) => p.labels[k] === v);
      return p.name.startsWith(name);
    });
  }

  relatedDeployment(item: Record<string, unknown>): K8sDeployment | undefined {
    const dep = item['deployment'] as string | undefined;
    if (!dep) return undefined;
    return this.cluster.deployments.find(d => d.name === dep && d.namespace === item['namespace']);
  }

  relatedServices(item: Record<string, unknown>): K8sService[] {
    const ns   = item['namespace'] as string;
    const labs = (item['labels'] ?? item['selector'] ?? {}) as Record<string, string>;
    return this.cluster.services.filter(s => {
      if (s.namespace !== ns) return false;
      return Object.entries(s.selector).every(([k, v]) => labs[k] === v);
    });
  }

  relatedReplicaSets(item: Record<string, unknown>): K8sReplicaSet[] {
    const name = item['name'] as string;
    const ns   = item['namespace'] as string;
    return this.cluster.replicasets.filter(rs => rs.deployment === name && rs.namespace === ns);
  }

  restartPod(pod: K8sPod): void {
    const idx = this.cluster.pods.findIndex(p => p.name === pod.name);
    if (idx >= 0) {
      this.cluster.pods[idx] = { ...pod, restarts: pod.restarts + 1, status: 'Running' };
      const sel = this.selectedItem();
      if (sel && sel['name'] === pod.name) this.selectedItem.set(this.cluster.pods[idx] as unknown as Record<string, unknown>);
    }
    this.showToast(`✅ Pod ${pod.name} restarted`);
  }

  deletePod(pod: K8sPod): void {
    this.cluster.pods = this.cluster.pods.filter(p => p.name !== pod.name);
    const sel = this.selectedItem();
    if (sel && sel['name'] === pod.name) this.selectedItem.set(null);
    this.closeConfirm();
    this.showToast(`🗑️ Pod ${pod.name} eliminato`);
  }

  openScaleModal(dep: K8sDeployment): void { this.scaleModal.set({ dep, value: dep.replicas }); }

  applyScale(): void {
    const m = this.scaleModal();
    if (!m) return;
    const idx = this.cluster.deployments.findIndex(d => d.name === m.dep.name);
    if (idx >= 0) {
      const r = m.value;
      const cur = parseInt(m.dep.ready.split('/')[0], 10);
      this.cluster.deployments[idx] = { ...m.dep, replicas: r, available: r, ready: `${Math.min(r, cur)}/${r}` };
      const sel = this.selectedItem();
      if (sel && sel['name'] === m.dep.name)
        this.selectedItem.set(this.cluster.deployments[idx] as unknown as Record<string, unknown>);
    }
    this.scaleModal.set(null);
    this.showToast(`⚖️ ${m.dep.name} scaled to ${m.value} replicas`);
  }

  restartDeployment(dep: K8sDeployment): void {
    this.showToast(`♻️ Deployment ${dep.name} rolling restart started`);
  }

  openDeletePodConfirm(pod: K8sPod): void {
    this.confirmModal.set({
      title: `Elimina Pod: ${pod.name}`,
      message: 'Sei sicuro? Kubernetes creerà un nuovo pod se gestito da un controller.',
      action: () => this.deletePod(pod),
    });
  }

  openDeleteDepConfirm(dep: K8sDeployment): void {
    this.confirmModal.set({
      title: `Elimina Deployment: ${dep.name}`,
      message: 'Tutti i pod gestiti verranno terminati.',
      action: () => {
        this.cluster.deployments = this.cluster.deployments.filter(d => d.name !== dep.name);
        const sel = this.selectedItem();
        if (sel && sel['name'] === dep.name) this.selectedItem.set(null);
        this.closeConfirm();
        this.showToast(`🗑️ Deployment ${dep.name} eliminato`);
      },
    });
  }

  closeConfirm(): void { this.confirmModal.set(null); }
  closeScale():   void { this.scaleModal.set(null); }
  confirmAction(): void { this.confirmModal()?.action(); }

  saveYaml(key: string): void {
    this.yamlSaved.update(m => ({ ...m, [key]: this.editorYaml() }));
    this.showToast(`💾 YAML salvato per ${key}`);
  }

  copyYaml(): void {
    navigator.clipboard.writeText(this.editorYaml()).then(() => this.showToast('📋 YAML copiato'));
  }

  showToast(msg: string, type: 'ok' | 'err' = 'ok'): void {
    this.toast.set({ msg, type });
    this.cdr.markForCheck();
    setTimeout(() => { this.toast.set(null); this.cdr.markForCheck(); }, 2800);
  }

  podLogs(pod: K8sPod): string { return podLogs(pod); }

  logLineClass(line: string): string {
    if (line.includes('ERROR') || line.includes('error')) return 'log-error';
    if (line.includes('WARN'))  return 'log-warn';
    if (line.includes('FATAL')) return 'log-fatal';
    return 'log-info';
  }

  badgeCount(section: Section): number {
    const ns = this.activeNs();
    const data = (this.cluster as unknown as Record<string, Array<{ namespace?: string }>>)[section];
    if (!data) return 0;
    return ns === 'all' ? data.length : data.filter(i => i.namespace === ns).length;
  }

  hasIssue(section: Section): boolean {
    const ns = this.activeNs();
    if (section === 'pods') {
      return this.cluster.pods
        .filter(p => ns === 'all' || p.namespace === ns)
        .some(p => ['CrashLoopBackOff', 'Error', 'Failed'].includes(p.status));
    }
    if (section === 'deployments') {
      return this.cluster.deployments
        .filter(d => ns === 'all' || d.namespace === ns)
        .some(d => d.ready.split('/')[0] === '0');
    }
    return false;
  }

  criticalPods(): K8sPod[] {
    return this.filterNs(this.cluster.pods)
      .filter(p => ['CrashLoopBackOff', 'Error', 'Failed'].includes(p.status));
  }

  yamlKey(): string {
    const item = this.selectedItem();
    return item ? `${this.selectedKind()}/${item['namespace']}/${item['name']}` : '';
  }

  depReady(d: K8sDeployment): boolean {
    const [a, b] = d.ready.split('/');
    return a === b;
  }

  objectEntries(o: unknown): [string, string][] {
    if (!o || typeof o !== 'object') return [];
    return Object.entries(o as Record<string, string>);
  }

  castPod(item: Record<string, unknown>): K8sPod         { return item as unknown as K8sPod; }
  castDep(item: Record<string, unknown>): K8sDeployment  { return item as unknown as K8sDeployment; }

  trackBy(_: number, item: Record<string, unknown>): string {
    return (item['name'] as string) ?? String(_);
  }
}
