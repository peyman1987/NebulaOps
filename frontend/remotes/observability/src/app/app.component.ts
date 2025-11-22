import { Component, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

interface Metric { name: string; value: string; unit: string; trend: 'up'|'down'|'stable'; status: 'ok'|'warn'|'danger'; }
interface Alert  { name: string; severity: 'critical'|'warning'|'info'; message: string; since: string; }

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
  styleUrl:    './app.component.css',
})
export class AppComponent implements OnInit {
  metrics = signal<Metric[]>([]);
  alerts  = signal<Alert[]>([]);
  services = signal<{ name: string; uptime: string; latency: string; errors: string; status: string }[]>([]);

  ngOnInit(): void {
    this.metrics.set([
      { name:'CPU Usage',        value:'34',    unit:'%',  trend:'stable', status:'ok' },
      { name:'Memory Usage',     value:'62',    unit:'%',  trend:'up',     status:'warn' },
      { name:'Pod Count',        value:'9',     unit:'',   trend:'stable', status:'ok' },
      { name:'Request Rate',     value:'1.2k',  unit:'/s', trend:'up',     status:'ok' },
      { name:'Error Rate',       value:'0.3',   unit:'%',  trend:'down',   status:'ok' },
      { name:'P99 Latency',      value:'142',   unit:'ms', trend:'stable', status:'ok' },
    ]);
    this.alerts.set([
      { name:'HighMemoryUsage',   severity:'warning',  message:'postgres: memory > 80%',               since:'18m ago' },
      { name:'PodCrashLoop',      severity:'critical', message:'auth-service: CrashLoopBackOff restart',since:'32m ago' },
      { name:'CertExpiringSoon',  severity:'warning',  message:'tls-cert scade tra 14 giorni',          since:'1h ago' },
    ]);
    this.services.set([
      { name:'api-gateway',    uptime:'99.98%', latency:'24ms',  errors:'0.02%', status:'ok' },
      { name:'auth-service',   uptime:'87.1%',  latency:'—',     errors:'100%',  status:'danger' },
      { name:'frontend',       uptime:'99.9%',  latency:'12ms',  errors:'0.01%', status:'ok' },
      { name:'postgres',       uptime:'100%',   latency:'4ms',   errors:'0%',    status:'ok' },
    ]);
  }

  trendIcon(t: string): string { return t === 'up' ? '↑' : t === 'down' ? '↓' : '→'; }
  sevClass(s: string): string { return s === 'critical' ? 'danger' : s === 'warning' ? 'warn' : 'ok'; }
}
