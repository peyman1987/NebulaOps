export type NebulaStatus = 'PASS' | 'WARN' | 'FAIL' | 'INFO' | 'PENDING' | 'READY';

export interface NebulaMetricCard {
  label: string;
  value: string | number;
  trend?: string;
  status?: NebulaStatus;
}

export interface NebulaTimelineEntry {
  id?: string;
  title: string;
  subtitle?: string;
  status: NebulaStatus;
  timestamp?: string;
  correlationId?: string;
}

export interface NebulaCommandItem {
  id: string;
  title: string;
  subtitle: string;
  group: string;
  icon?: string;
  action: 'remote' | 'link' | 'api' | 'command';
  target: string;
}

export const NEBULAOPS_UI_TOKENS = {
  colors: {
    bg: '#050816',
    card: '#0b1b34',
    cyan: '#2fe6ff',
    purple: '#9b5cff',
    green: '#70f0b4',
    amber: '#ffb347',
    red: '#ff6b88'
  },
  radius: {
    card: '22px',
    pill: '999px',
    modal: '28px'
  }
} as const;
