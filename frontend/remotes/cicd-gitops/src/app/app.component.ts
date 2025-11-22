import { Component, OnInit, signal, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';

type PipelineStatus = 'success'|'running'|'failed'|'pending';
interface Pipeline { id: string; name: string; branch: string; commit: string; status: PipelineStatus; duration: string; triggered: string; }
interface ArgoApp  { name: string; namespace: string; syncStatus: string; healthStatus: string; revision: string; }

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
  styleUrl:    './app.component.css',
})
export class AppComponent implements OnInit {
  pipelines = signal<Pipeline[]>([]);
  argoApps  = signal<ArgoApp[]>([]);
  toast     = signal('');
  constructor(private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.pipelines.set([
      { id:'#214', name:'nebulaops-app',    branch:'main',          commit:'f3a9c12', status:'success', duration:'4m 12s', triggered:'2h ago' },
      { id:'#213', name:'auth-service',     branch:'fix/crash-loop', commit:'b7e2d45', status:'failed',  duration:'1m 34s', triggered:'3h ago' },
      { id:'#212', name:'frontend',         branch:'feat/v22.2',    commit:'9c4f8a1', status:'success', duration:'6m 50s', triggered:'5h ago' },
      { id:'#211', name:'ai-ops-service',   branch:'develop',       commit:'2d7b3e9', status:'running', duration:'2m 10s', triggered:'10m ago' },
      { id:'#210', name:'finops-service',   branch:'main',          commit:'e1a5f67', status:'pending', duration:'—',      triggered:'just now' },
    ]);
    this.argoApps.set([
      { name:'nebulaops-production', namespace:'production', syncStatus:'Synced',    healthStatus:'Healthy',   revision:'f3a9c12' },
      { name:'monitoring-stack',     namespace:'monitoring', syncStatus:'Synced',    healthStatus:'Healthy',   revision:'3b8d210' },
      { name:'auth-service-prod',    namespace:'production', syncStatus:'OutOfSync', healthStatus:'Degraded',  revision:'b7e2d45' },
      { name:'cert-manager',         namespace:'cert-manager',syncStatus:'Synced',   healthStatus:'Healthy',   revision:'v1.14.5' },
    ]);
  }

  sc(s: string): string {
    if (['success','synced','healthy'].includes(s.toLowerCase())) return 'ok';
    if (['failed','outofsync','degraded'].includes(s.toLowerCase())) return 'danger';
    if (['running','progressing'].includes(s.toLowerCase())) return 'warn';
    return '';
  }

  retryPipeline(p: Pipeline): void {
    this.pipelines.update(list => list.map(x => x.id === p.id ? {...x, status:'running'} : x));
    this.showToast(`♻️ Pipeline ${p.id} restarted`);
  }
  syncApp(a: ArgoApp): void {
    this.argoApps.update(list => list.map(x => x.name === a.name ? {...x, syncStatus:'Synced', healthStatus:'Healthy'} : x));
    this.showToast(`🔄 ${a.name} sincronizzata`);
  }
  showToast(msg: string): void {
    this.toast.set(msg);
    this.cdr.markForCheck();
    setTimeout(() => { this.toast.set(''); this.cdr.markForCheck(); }, 2400);
  }
}
