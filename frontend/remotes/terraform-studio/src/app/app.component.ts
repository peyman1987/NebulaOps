import { Component, OnInit, signal, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';

interface TfModule { name: string; version: string; source: string; environment: string; status: 'ok'|'drift'|'pending'; lastApply: string; }
interface TfRun    { id: string; workspace: string; operation: 'plan'|'apply'|'destroy'; status: string; initiated: string; }

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
  styleUrl:    './app.component.css',
})
export class AppComponent implements OnInit {
  modules    = signal<TfModule[]>([]);
  runs       = signal<TfRun[]>([]);
  toast      = signal('');
  planOutput = signal('');
  constructor(private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.modules.set([
      { name:'eks-cluster',      version:'20.24.0', source:'terraform-aws-modules/eks/aws',     environment:'production', status:'ok',      lastApply:'2d ago' },
      { name:'rds-postgres',     version:'10.6.0',  source:'terraform-aws-modules/rds/aws',     environment:'production', status:'drift',   lastApply:'5d ago' },
      { name:'vpc',              version:'5.8.4',   source:'terraform-aws-modules/vpc/aws',     environment:'production', status:'ok',      lastApply:'1w ago' },
      { name:'monitoring-stack', version:'1.2.0',   source:'./modules/monitoring',              environment:'staging',    status:'pending', lastApply:'never' },
    ]);
    this.runs.set([
      { id:'run-0094', workspace:'production', operation:'apply',   status:'applied',  initiated:'2d ago' },
      { id:'run-0093', workspace:'production', operation:'plan',    status:'planned',  initiated:'2d ago' },
      { id:'run-0092', workspace:'staging',    operation:'plan',    status:'running',  initiated:'5m ago' },
      { id:'run-0091', workspace:'production', operation:'destroy', status:'errored',  initiated:'3d ago' },
    ]);
  }

  sc(s: string): string {
    if (['ok','applied','planned'].includes(s)) return 'ok';
    if (['errored','drift'].includes(s)) return 'danger';
    if (['running','pending'].includes(s)) return 'warn';
    return '';
  }

  runPlan(m: TfModule): void {
    this.planOutput.set(`Refreshing state...
  aws_eks_cluster.main: Refreshing state...
  aws_rds_instance.postgres: Refreshing state... [id=prod-postgres]

  Terraform will perform the following actions:
    ~ aws_rds_instance.postgres
        instance_class: "db.t3.medium" -> "db.t3.large"
        multi_az: false -> true

  Plan: 0 to add, 2 to change, 0 to destroy.`);
    this.showToast(`🔍 Plan executed for ${m.name}`);
  }

  showToast(msg: string): void {
    this.toast.set(msg);
    this.cdr.markForCheck();
    setTimeout(() => { this.toast.set(''); this.cdr.markForCheck(); }, 2400);
  }
}
