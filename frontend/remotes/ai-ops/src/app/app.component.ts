import { Component, OnInit, signal, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule }  from '@angular/forms';

interface Incident {
  id: string; title: string;
  severity: 'critical' | 'high' | 'medium';
  status: 'open' | 'investigating' | 'resolved';
  rca: string; suggestion: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit {
  incidents  = signal<Incident[]>([]);
  question   = signal('');
  aiResponse = signal('');
  analyzing  = signal(false);
  toast      = signal('');

  constructor(private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.incidents.set([
      {
        id: 'INC-001', title: 'auth-service CrashLoopBackOff',
        severity: 'critical', status: 'investigating',
        rca: 'Container non riesce a connettersi a postgres:5432. Il secret postgres-credentials è scaduto.',
        suggestion: '1. Ruota il secret keycloak-admin\n2. Riavvia auth-service deployment\n3. Verifica NetworkPolicy tra namespace',
      },
      {
        id: 'INC-002', title: 'ai-ops pod in Pending da 5 min',
        severity: 'high', status: 'open',
        rca: "ImagePullBackOff: l'immagine nebulaops-ai:1.0 non trovata nel registry privato.",
        suggestion: '1. Verifica registry-pull-secret\n2. Controlla tag immagine nel deployment YAML\n3. Esegui docker pull manuale per validare',
      },
      {
        id: 'INC-003', title: 'Alta latenza api-gateway (P99 > 500ms)',
        severity: 'medium', status: 'resolved',
        rca: 'Picco di traffico su /api/kubernetes/snapshot causato da polling aggressivo del MFE.',
        suggestion: 'Cache aggiunta a /api/kubernetes/snapshot. Resolved.',
      },
    ]);
  }

  sc(s: string): string {
    if (['critical', 'open'].includes(s)) return 'danger';
    if (['high', 'investigating'].includes(s)) return 'warn';
    return 'ok';
  }

  autofix(i: Incident): void {
    this.incidents.update(list => list.map(x => x.id === i.id ? { ...x, status: 'resolved' } : x));
    this.showToast(`🤖 Autofix applied for ${i.id}`);
  }

  askAi(): void {
    if (!this.question().trim()) return;
    this.analyzing.set(true);
    this.cdr.markForCheck();
    setTimeout(() => {
      const q = this.question().toLowerCase();
      let resp = 'Analyzing the cluster...\n\n';
      if (q.includes('auth') || q.includes('crash')) {
        resp += '⚠️ RCA: auth-service fails because the database connection is denied.\n✅ Fix: rotate the postgres-credentials secret and restart the deployment.';
      } else if (q.includes('memory') || q.includes('ram')) {
        resp += "📊 Postgres uses 82% of the available memory (limit: 512Mi).\n✅ Fix: increase the memory limit to 1Gi or add a VPA.";
      } else {
        resp += '✅ No critical anomaly detected in the last 24h.\n📈 Average CPU: 34% · Memory: 62% · Errors: 0.3%';
      }
      this.aiResponse.set(resp);
      this.analyzing.set(false);
      this.question.set('');
      this.cdr.markForCheck();
    }, 1200);
  }

  showToast(msg: string): void {
    this.toast.set(msg);
    this.cdr.markForCheck();
    setTimeout(() => { this.toast.set(''); this.cdr.markForCheck(); }, 2400);
  }
}
