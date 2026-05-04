// Live-only Kubernetes type declarations retained for compatibility.
// This file intentionally contains no seeded Kubernetes objects.
export interface K8sPod { [key: string]: unknown }
export interface K8sDeployment { [key: string]: unknown }
export interface K8sService { [key: string]: unknown }
export interface K8sReplicaSet { [key: string]: unknown }
export interface K8sNode { [key: string]: unknown }
export interface K8sStatefulSet { [key: string]: unknown }
export interface K8sEvent { [key: string]: unknown }
export interface K8sConfigMap { [key: string]: unknown }
export interface K8sSecret { [key: string]: unknown }
export interface HelmRelease { [key: string]: unknown }
export interface ClusterSnapshot { [key: string]: unknown }
export function emptyCluster(): ClusterSnapshot { return {}; }
