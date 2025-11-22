import { Component, OnInit, signal, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule }  from '@angular/forms';
import { HttpClient }   from '@angular/common/http';

interface Container { id: string; name: string; image: string; status: string; ports: string; created: string; }
interface DockerImage { id: string; repository: string; tag: string; size: string; created: string; }
interface DockerVolume { name: string; driver: string; mountpoint: string; }

function mockContainers(): Container[] {
  return [
    { id:'a1b2c3d4', name:'nebulaops-gateway',    image:'nebulaops-gateway:22.2',    status:'running', ports:'8080:8080',    created:'2 days ago' },
    { id:'e5f6g7h8', name:'keycloak',              image:'quay.io/keycloak/keycloak:24', status:'running', ports:'8180:8080', created:'2 days ago' },
    { id:'i9j0k1l2', name:'postgres',              image:'postgres:16-alpine',         status:'running', ports:'5432:5432',   created:'3 days ago' },
    { id:'m3n4o5p6', name:'redis',                 image:'redis:7-alpine',             status:'running', ports:'6379:6379',   created:'3 days ago' },
    { id:'q7r8s9t0', name:'rabbitmq',              image:'rabbitmq:3-management',      status:'running', ports:'5672:5672',   created:'2 days ago' },
    { id:'u1v2w3x4', name:'prometheus',            image:'prom/prometheus:v2.52',      status:'running', ports:'9090:9090',   created:'1 day ago' },
    { id:'y5z6a7b8', name:'grafana',               image:'grafana/grafana:10.4',       status:'running', ports:'3000:3000',   created:'1 day ago' },
    { id:'c9d0e1f2', name:'mongo',                 image:'mongo:7',                    status:'running', ports:'27017:27017', created:'3 days ago' },
    { id:'g3h4i5j6', name:'mfe-openlens',          image:'nebulaops-mfe-openlens:22.2',status:'running', ports:'4212:80',     created:'1 day ago' },
    { id:'k7l8m9n0', name:'mfe-task-management',   image:'nebulaops-mfe-tasks:22.2',   status:'exited',  ports:'4213:80',     created:'5 hours ago' },
  ];
}
function mockImages(): DockerImage[] {
  return [
    { id:'sha256:a1b2', repository:'nebulaops-gateway',     tag:'22.2',      size:'142MB', created:'2 days ago' },
    { id:'sha256:c3d4', repository:'postgres',              tag:'16-alpine', size:'238MB', created:'1 week ago' },
    { id:'sha256:e5f6', repository:'redis',                 tag:'7-alpine',  size:'34MB',  created:'1 week ago' },
    { id:'sha256:g7h8', repository:'grafana/grafana',       tag:'10.4',      size:'466MB', created:'3 days ago' },
    { id:'sha256:i9j0', repository:'prom/prometheus',       tag:'v2.52',     size:'295MB', created:'4 days ago' },
    { id:'sha256:k1l2', repository:'nginx',                 tag:'1.27-alpine',size:'43MB', created:'1 week ago' },
  ];
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
  styleUrl:    './app.component.css',
})
export class AppComponent implements OnInit {
  activeTab = signal<'containers'|'images'|'volumes'>('containers');
  containers = signal<Container[]>([]);
  images     = signal<DockerImage[]>([]);
  volumes    = signal<{ name: string; driver: string; mountpoint: string }[]>([]);
  toast      = signal<string|''>('');

  constructor(private http: HttpClient, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.containers.set(mockContainers());
    this.images.set(mockImages());
    this.volumes.set([
      { name:'postgres_data', driver:'local', mountpoint:'/var/lib/docker/volumes/postgres_data/_data' },
      { name:'redis_data',    driver:'local', mountpoint:'/var/lib/docker/volumes/redis_data/_data' },
      { name:'grafana_data',  driver:'local', mountpoint:'/var/lib/docker/volumes/grafana_data/_data' },
    ]);
  }

  sc(s: string): string {
    const st = s.toLowerCase();
    if (st.includes('run')) return 'ok';
    if (st.includes('exit') || st.includes('dead')) return 'danger';
    return 'warn';
  }

  stopContainer(c: Container): void {
    this.containers.update(list => list.map(x => x.id === c.id ? {...x, status:'exited'} : x));
    this.showToast(`⏹ ${c.name} stopped`);
  }
  startContainer(c: Container): void {
    this.containers.update(list => list.map(x => x.id === c.id ? {...x, status:'running'} : x));
    this.showToast(`▶ ${c.name} started`);
  }
  removeContainer(c: Container): void {
    if (c.status === 'running') { this.showToast(`❌ Stop the container before removing it`); return; }
    this.containers.update(list => list.filter(x => x.id !== c.id));
    this.showToast(`🗑️ ${c.name} removed`);
  }
  pruneImages(): void {
    this.showToast('🧹 Dangling images removed');
  }
  showToast(msg: string): void {
    this.toast.set(msg);
    this.cdr.markForCheck();
    setTimeout(() => { this.toast.set(''); this.cdr.markForCheck(); }, 2600);
  }
}
