import { Component, OnInit, signal, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';

interface Vuln { id: string; severity: 'critical'|'high'|'medium'|'low'; package: string; version: string; fixedIn: string; image: string; }
interface SecretLeak { id: string; type: string; location: string; riskLevel: 'critical'|'high'|'medium'; status: 'open'|'resolved'; }

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
  styleUrl:    './app.component.css',
})
export class AppComponent implements OnInit {
  vulns   = signal<Vuln[]>([]);
  secrets = signal<SecretLeak[]>([]);
  toast   = signal('');
  activeTab = signal<'vulns'|'secrets'>('vulns');
  constructor(private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.vulns.set([
      { id:'CVE-2024-1234', severity:'critical', package:'openssl',     version:'3.1.4', fixedIn:'3.1.5', image:'nebulaops-auth:1.4' },
      { id:'CVE-2024-5678', severity:'high',     package:'libxml2',     version:'2.9.14',fixedIn:'2.9.15',image:'nginx:1.27-alpine' },
      { id:'CVE-2023-9012', severity:'high',     package:'zlib',        version:'1.2.13',fixedIn:'1.2.14',image:'postgres:16-alpine' },
      { id:'CVE-2024-3456', severity:'medium',   package:'busybox',     version:'1.36.0',fixedIn:'1.36.1',image:'redis:7-alpine' },
      { id:'CVE-2024-7890', severity:'low',      package:'curl',        version:'8.4.0', fixedIn:'8.5.0', image:'nginx:1.27-alpine' },
    ]);
    this.secrets.set([
      { id:'S-001', type:'AWS Access Key',    location:'src/config/aws.js:14',          riskLevel:'critical', status:'open' },
      { id:'S-002', type:'JWT Secret',        location:'.env:8',                        riskLevel:'critical', status:'resolved' },
      { id:'S-003', type:'Database Password', location:'docker-compose.yml:42',         riskLevel:'high',     status:'open' },
      { id:'S-004', type:'API Key',           location:'src/services/stripe.ts:3',      riskLevel:'high',     status:'open' },
    ]);
  }

  sc(s: string): string {
    if (['critical'].includes(s)) return 'danger';
    if (['high'].includes(s))     return 'warn';
    if (['medium','open'].includes(s)) return 'warn';
    return 'ok';
  }
  resolve(s: SecretLeak): void {
    this.secrets.update(list => list.map(x => x.id === s.id ? {...x, status:'resolved'} : x));
    this.showToast(`✅ ${s.id} contrassegnato come risolto`);
  }
  showToast(msg: string): void {
    this.toast.set(msg);
    this.cdr.markForCheck();
    setTimeout(() => { this.toast.set(''); this.cdr.markForCheck(); }, 2400);
  }
}
