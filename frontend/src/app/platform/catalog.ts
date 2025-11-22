export type RemoteId = 'docker-desktop' | 'infra-hub' | 'openlens-kubernetes' | 'task-management' | 'observability' | 'cicd-gitops' | 'terraform-studio' | 'devsecops' | 'ai-ops' | 'finops-cost';
export type ServiceGroup = 'Identity' | 'Runtime' | 'Infra' | 'Observability' | 'Data' | 'DevOps' | 'Micro Frontend';

export interface RemoteDefinition {
  id: RemoteId;
  tag: string;
  title: string;
  subtitle: string;
  icon: string;
  port: number;
  path: string;
  color: string;
  scope: string;
  group: string;
  service: string;
  status: string;
}

export interface ServiceLink {
  title: string;
  subtitle: string;
  url: string;
  icon: string;
  port: string;
  group: ServiceGroup;
}

export interface SidebarGroupDefinition {
  label: string;
  icon: string;
  groups: readonly string[];
}

export const REMOTES: RemoteDefinition[] = [
  {
    id: 'docker-desktop',
    tag: 'nebulaops-mfe-docker-desktop',
    title: 'Docker Desktop',
    subtitle: 'Container lifecycle, images, volumes, networks and Docker runtime actions through the gateway.',
    icon: '🐳',
    port: 4211,
    color: 'cyan',
    scope: 'Runtime · Docker',
    group: 'Runtime',
    service: 'mfe-docker-desktop',
    path: 'http://localhost:4211/remoteEntry.js',
    status: 'remote app · independently deployable'
  },
  {
    id: 'infra-hub',
    tag: 'nebulaops-mfe-infra-hub',
    title: 'INFRA Hub',
    subtitle: 'Infrastructure console catalog for observability, data, gateway, GitOps and platform access points.',
    icon: '🏗️',
    port: 4220,
    color: 'cyan',
    scope: 'Infra · Console Hub',
    group: 'Infra',
    service: 'mfe-infra-hub',
    path: 'http://localhost:4220/remoteEntry.js',
    status: 'remote app · independently deployable'
  },
  {
    id: 'openlens-kubernetes',
    tag: 'nebulaops-mfe-openlens-kubernetes',
    title: 'OpenLens Kubernetes',
    subtitle: 'Cluster, namespace, workload, service, event and Helm release visibility.',
    icon: '☸️',
    port: 4212,
    color: 'blue',
    scope: 'Runtime · Kubernetes',
    group: 'Infra',
    service: 'mfe-openlens-kubernetes',
    path: 'http://localhost:4212/remoteEntry.js',
    status: 'remote app · independently deployable'
  },
  {
    id: 'task-management',
    tag: 'nebulaops-mfe-task-management',
    title: 'Task Management',
    subtitle: 'Backlog, priorities, ownership, RabbitMQ events and operational notifications.',
    icon: '✅',
    port: 4213,
    color: 'violet',
    scope: 'Delivery · Tasks',
    group: 'Delivery',
    service: 'mfe-task-management',
    path: 'http://localhost:4213/remoteEntry.js',
    status: 'remote app · independently deployable'
  },
  {
    id: 'observability',
    tag: 'nebulaops-mfe-observability',
    title: 'Observability',
    subtitle: 'Grafana, Prometheus, Loki, Tempo, OpenTelemetry and platform KPIs.',
    icon: '📈',
    port: 4214,
    color: 'green',
    scope: 'SRE · Metrics/Logs/Traces',
    group: 'SRE',
    service: 'mfe-observability',
    path: 'http://localhost:4214/remoteEntry.js',
    status: 'remote app · independently deployable'
  },
  {
    id: 'cicd-gitops',
    tag: 'nebulaops-mfe-cicd-gitops',
    title: 'CI/CD + GitOps',
    subtitle: 'Pipelines, optional GitLab, Helm, ArgoCD and promotion flow management.',
    icon: '🚀',
    port: 4215,
    color: 'orange',
    scope: 'DevOps · Pipelines/GitOps',
    group: 'DevOps',
    service: 'mfe-cicd-gitops',
    path: 'http://localhost:4215/remoteEntry.js',
    status: 'remote app · independently deployable'
  },
  {
    id: 'terraform-studio',
    tag: 'nebulaops-mfe-terraform-studio',
    title: 'Terraform Studio',
    subtitle: 'Terraform plan, validate and local apply flows with environments and reusable modules.',
    icon: '🧱',
    port: 4216,
    color: 'purple',
    scope: 'IaC · Environments',
    group: 'Infra',
    service: 'mfe-terraform-studio',
    path: 'http://localhost:4216/remoteEntry.js',
    status: 'remote app · independently deployable'
  },
  {
    id: 'devsecops',
    tag: 'nebulaops-mfe-devsecops',
    title: 'DevSecOps',
    subtitle: 'Vulnerability findings, secret posture, Trivy scans, policies and hardening workflows.',
    icon: '🛡️',
    port: 4217,
    color: 'red',
    scope: 'Security · Secrets/Vulns',
    group: 'Security',
    service: 'mfe-devsecops',
    path: 'http://localhost:4217/remoteEntry.js',
    status: 'remote app · independently deployable'
  },
  {
    id: 'ai-ops',
    tag: 'nebulaops-mfe-ai-ops',
    title: 'AI Ops',
    subtitle: 'Root-cause analysis, operational insights, anomaly hints and platform assistance.',
    icon: '🤖',
    port: 4218,
    color: 'pink',
    scope: 'AI · RCA/Assist',
    group: 'AI',
    service: 'mfe-ai-ops',
    path: 'http://localhost:4218/remoteEntry.js',
    status: 'remote app · independently deployable'
  },
  {
    id: 'finops-cost',
    tag: 'nebulaops-mfe-finops-cost',
    title: 'FinOps Cost',
    subtitle: 'Cost analytics, resource spend, forecast and optimization reporting.',
    icon: '💸',
    port: 4219,
    color: 'amber',
    scope: 'Cost · Analytics',
    group: 'FinOps',
    service: 'mfe-finops-cost',
    path: 'http://localhost:4219/remoteEntry.js',
    status: 'remote app · independently deployable'
  }
] as RemoteDefinition[];

export const SERVICE_LINKS: ServiceLink[] = [
  { title: 'Keycloak', subtitle: 'Identity Provider', url: 'http://localhost:8180', icon: '🔑', port: '8180', group: 'Identity' },
  { title: 'Gateway', subtitle: 'BFF + API proxy', url: 'http://localhost:8080/actuator/health', icon: '🚪', port: '8080', group: 'Runtime' },
  { title: 'INFRA Hub MFE', subtitle: 'Infra · Console Hub', url: 'http://localhost:4220', icon: '🏗️', port: '4220', group: 'Micro Frontend' },
  { title: 'FinOps Cost MFE', subtitle: 'Cost · Analytics', url: 'http://localhost:4219', icon: '💸', port: '4219', group: 'Micro Frontend' },
  { title: 'AI Ops MFE', subtitle: 'AI · RCA/Assist', url: 'http://localhost:4218', icon: '🤖', port: '4218', group: 'Micro Frontend' },
  { title: 'DevSecOps MFE', subtitle: 'Security · Secrets/Vulns', url: 'http://localhost:4217', icon: '🛡️', port: '4217', group: 'Micro Frontend' },
  { title: 'Terraform Studio MFE', subtitle: 'IaC · Environments', url: 'http://localhost:4216', icon: '🧱', port: '4216', group: 'Micro Frontend' },
  { title: 'CI/CD + GitOps MFE', subtitle: 'DevOps · Pipelines/GitOps', url: 'http://localhost:4215', icon: '🚀', port: '4215', group: 'Micro Frontend' },
  { title: 'Observability MFE', subtitle: 'SRE · Metrics/Logs/Traces', url: 'http://localhost:4214', icon: '📈', port: '4214', group: 'Micro Frontend' },
  { title: 'Task Management MFE', subtitle: 'Delivery · Tasks', url: 'http://localhost:4213', icon: '✅', port: '4213', group: 'Micro Frontend' },
  { title: 'OpenLens Kubernetes MFE', subtitle: 'Runtime · Kubernetes', url: 'http://localhost:4212', icon: '☸️', port: '4212', group: 'Micro Frontend' },
  { title: 'Docker Desktop MFE', subtitle: 'Runtime · Docker', url: 'http://localhost:4211', icon: '🐳', port: '4211', group: 'Micro Frontend' },
  { title: 'Grafana', subtitle: 'Dashboards + traces', url: 'http://localhost:3000', icon: '🌀', port: '3000', group: 'Observability' },
  { title: 'Prometheus', subtitle: 'Metrics query engine', url: 'http://localhost:9090', icon: '▲', port: '9090', group: 'Observability' },
  { title: 'Loki', subtitle: 'Log aggregation readiness', url: 'http://localhost:3100/ready', icon: '≋', port: '3100', group: 'Observability' },
  { title: 'Tempo', subtitle: 'Distributed tracing readiness', url: 'http://localhost:3200/ready', icon: '⌁', port: '3200', group: 'Observability' },
  { title: 'OpenTelemetry Collector', subtitle: 'OTLP ingestion endpoint', url: 'http://localhost:4318', icon: '◎', port: '4318', group: 'Observability' },
  { title: 'RabbitMQ', subtitle: 'Queue management console', url: 'http://localhost:15672', icon: '✉️', port: '15672', group: 'Data' },
  { title: 'Mongo Express', subtitle: 'Document database UI', url: 'http://localhost:8088', icon: '🍃', port: '8088', group: 'Data' },
  { title: 'Redis Commander', subtitle: 'Cache browser', url: 'http://localhost:8089', icon: '🔴', port: '8089', group: 'Data' },
  { title: 'GitLab', subtitle: 'Optional CI/CD profile', url: 'http://localhost:8929', icon: '🦊', port: '8929', group: 'DevOps' },
  { title: 'ArgoCD Console', subtitle: 'GitOps sync and rollback console', url: 'http://localhost:8081', icon: '∞', port: '8081', group: 'DevOps' },
] as ServiceLink[];

export const SIDEBAR_GROUPS: SidebarGroupDefinition[] = [
  { label: 'Runtime',      icon: '⚙️', groups: ['Runtime'] },
  { label: 'INFRA',        icon: '🏗️', groups: ['Infra'] },
  { label: 'Delivery',     icon: '📦', groups: ['Delivery'] },
  { label: 'SRE',          icon: '📡', groups: ['SRE'] },
  { label: 'DevOps',       icon: '🔧', groups: ['DevOps'] },
  { label: 'Security & AI', icon: '🔐', groups: ['Security', 'AI', 'FinOps'] },
];

export function remoteSearchText(remote: RemoteDefinition): string {
  return [
    remote.title,
    remote.subtitle,
    remote.scope,
    remote.group,
    remote.service,
    remote.port,
    remote.id,
    remote.tag,
  ].join(' ').toLowerCase();
}
