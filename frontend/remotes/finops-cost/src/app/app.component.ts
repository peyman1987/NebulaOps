import { Component, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

interface CostEntry { namespace: string; service: string; cpu_cost: string; memory_cost: string; storage_cost: string; total: string; trend: string; }

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
  styleUrl:    './app.component.css',
})
export class AppComponent implements OnInit {
  costs     = signal<CostEntry[]>([]);
  totalMonth = signal('$1,842');
  forecast   = signal('$2,104');
  savings    = signal('$312');

  ngOnInit(): void {
    this.costs.set([
      { namespace:'production', service:'api-gateway',  cpu_cost:'$48',  memory_cost:'$22',  storage_cost:'$4',   total:'$74',  trend:'+5%' },
      { namespace:'production', service:'postgres',     cpu_cost:'$32',  memory_cost:'$68',  storage_cost:'$24',  total:'$124', trend:'+12%' },
      { namespace:'production', service:'auth-service', cpu_cost:'$18',  memory_cost:'$14',  storage_cost:'$2',   total:'$34',  trend:'-3%' },
      { namespace:'monitoring', service:'prometheus',   cpu_cost:'$24',  memory_cost:'$56',  storage_cost:'$18',  total:'$98',  trend:'+8%' },
      { namespace:'monitoring', service:'grafana',      cpu_cost:'$12',  memory_cost:'$18',  storage_cost:'$4',   total:'$34',  trend:'0%' },
      { namespace:'production', service:'redis',        cpu_cost:'$8',   memory_cost:'$12',  storage_cost:'$2',   total:'$22',  trend:'-1%' },
    ]);
  }

  trendClass(t: string): string {
    if (t.startsWith('+') && t !== '+0%') return 'warn';
    if (t.startsWith('-')) return 'ok';
    return '';
  }
}
