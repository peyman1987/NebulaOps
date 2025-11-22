import { Component, OnInit, signal, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule }  from '@angular/forms';

type Priority = 'critical'|'high'|'medium'|'low';
type Status   = 'todo'|'in-progress'|'done'|'blocked';
interface Task { id: string; title: string; priority: Priority; status: Status; assignee: string; labels: string[]; }

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
  styleUrl:    './app.component.css',
})
export class AppComponent implements OnInit {
  tasks = signal<Task[]>([]);
  filter = signal<Status|'all'>('all');
  toast  = signal('');

  constructor(private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.tasks.set([
      { id:'T-001', title:'Aggiorna cert-manager a v1.14.5',              priority:'high',     status:'todo',        assignee:'peyman',   labels:['infra','k8s'] },
      { id:'T-002', title:'Migrare auth-service da JWT a Keycloak',        priority:'critical', status:'in-progress', assignee:'sara',     labels:['security','backend'] },
      { id:'T-003', title:'Dashboard FinOps per namespace production',     priority:'medium',   status:'in-progress', assignee:'alex',     labels:['finops','frontend'] },
      { id:'T-004', title:'Aggiungere HPA al deployment api-gateway',      priority:'high',     status:'todo',        assignee:'peyman',   labels:['k8s','autoscaling'] },
      { id:'T-005', title:'Configurare Loki per log retention 30d',        priority:'medium',   status:'done',        assignee:'sara',     labels:['observability'] },
      { id:'T-006', title:'Review vulnerability report Trivy Q2',          priority:'high',     status:'blocked',     assignee:'alex',     labels:['security','devsecops'] },
      { id:'T-007', title:'Terraform module per RDS Aurora',               priority:'low',      status:'todo',        assignee:'peyman',   labels:['iac','aws'] },
      { id:'T-008', title:'Smoke test pipeline post-deploy su staging',    priority:'medium',   status:'done',        assignee:'sara',     labels:['cicd','testing'] },
    ]);
  }

  filtered(): Task[] {
    const f = this.filter();
    return f === 'all' ? this.tasks() : this.tasks().filter(t => t.status === f);
  }

  prioClass(p: Priority): string {
    return { critical:'danger', high:'warn', medium:'ok', low:'' }[p] ?? '';
  }
  statusClass(s: Status): string {
    return { 'in-progress':'ok', 'done':'ok', blocked:'danger', todo:'' }[s] ?? '';
  }

  moveStatus(t: Task, s: Status): void {
    this.tasks.update(list => list.map(x => x.id === t.id ? {...x, status:s} : x));
    this.showToast(`✅ ${t.id} → ${s}`);
  }

  showToast(msg: string): void {
    this.toast.set(msg);
    this.cdr.markForCheck();
    setTimeout(() => { this.toast.set(''); this.cdr.markForCheck(); }, 2400);
  }
}
