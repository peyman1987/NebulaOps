import { Component, ChangeDetectionStrategy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

interface InfraLink {
  title: string;
  description: string;
  url: string;
  icon: string;
  status: string;
  group: 'Observability' | 'Data' | 'Runtime' | 'DevOps' | 'Micro Frontend';
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  search = signal('');
  toast = signal<string | ''>('');

  readonly infraLinks: InfraLink[] = [
    { title: 'Grafana', description: 'Dashboards, Loki logs, Tempo traces and runtime metrics.', url: 'http://localhost:3000', icon: '🌀', status: 'localhost:3000', group: 'Observability' },
    { title: 'Prometheus', description: 'Metrics query engine, target state and service discovery.', url: 'http://localhost:9090', icon: '▲', status: 'localhost:9090', group: 'Observability' },
    { title: 'Loki', description: 'Centralized log backend used by Grafana Explore.', url: 'http://localhost:3100/ready', icon: '≋', status: 'localhost:3100', group: 'Observability' },
    { title: 'Tempo', description: 'Distributed tracing backend and span storage readiness.', url: 'http://localhost:3200/ready', icon: '⌁', status: 'localhost:3200', group: 'Observability' },
    { title: 'OpenTelemetry Collector', description: 'OTLP traces, metrics and logs ingestion endpoint.', url: 'http://localhost:4318', icon: '◎', status: 'localhost:4318', group: 'Observability' },
    { title: 'RabbitMQ', description: 'Queue dashboard, exchanges, bindings and consumers.', url: 'http://localhost:15672', icon: '✉️', status: 'localhost:15672', group: 'Data' },
    { title: 'Mongo Express', description: 'MongoDB collections, documents and index inspection.', url: 'http://localhost:8088', icon: '🍃', status: 'localhost:8088', group: 'Data' },
    { title: 'Redis Commander', description: 'Redis browser, keys, cache inspection and commands.', url: 'http://localhost:8089', icon: '🔴', status: 'localhost:8089', group: 'Data' },
    { title: 'Gateway API', description: 'Public API entry point and actuator health check.', url: 'http://localhost:8080/actuator/health', icon: '🚪', status: 'localhost:8080', group: 'Runtime' },
    { title: 'ArgoCD Console', description: 'GitOps application console, sync and rollback.', url: 'http://localhost:8081', icon: '∞', status: 'localhost:8081', group: 'DevOps' },
    { title: 'Pipeline Engine API', description: 'CI/CD Pipeline Designer backend service health.', url: 'http://localhost:8087/actuator/health', icon: '⚙️', status: 'localhost:8087', group: 'DevOps' },
    { title: 'Docker Desktop MFE', description: 'Containers, images, volumes and Docker runtime actions.', url: 'http://localhost:4211', icon: '🐳', status: 'localhost:4211', group: 'Micro Frontend' },
    { title: 'OpenLens Kubernetes MFE', description: 'Pods, deployments, services, nodes, events and Helm releases.', url: 'http://localhost:4212', icon: '☸️', status: 'localhost:4212', group: 'Micro Frontend' },
    { title: 'Terraform Studio MFE', description: 'Terraform plan, validate, apply, workspaces and drift checks.', url: 'http://localhost:4216', icon: '🧱', status: 'localhost:4216', group: 'Micro Frontend' },
  ];

  filteredLinks(): InfraLink[] {
    const term = this.search().trim().toLowerCase();
    if (!term) return this.infraLinks;
    return this.infraLinks.filter(link => [link.title, link.description, link.group, link.status].join(' ').toLowerCase().includes(term));
  }

  open(link: InfraLink): void {
    window.open(link.url, '_blank', 'noopener,noreferrer');
    this.showToast(`Opened ${link.title}`);
  }

  showToast(message: string): void {
    this.toast.set(message);
    window.setTimeout(() => this.toast.set(''), 2200);
  }
}
