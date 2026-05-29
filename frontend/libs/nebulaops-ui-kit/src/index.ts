export type NebulaStatus = 'PASS' | 'WARN' | 'FAIL' | 'INFO' | 'PENDING' | 'READY' | 'DEGRADED' | 'UNAVAILABLE' | 'NOT_CONFIGURED';

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

export interface NebulaActionBarAction {
  id: string;
  label: string;
  kind?: 'primary' | 'secondary' | 'safe' | 'danger';
  disabled?: boolean;
}

export interface NebulaSidePanelSection {
  title: string;
  rows: Array<{label: string; value: string | number; status?: NebulaStatus}>;
}

export const NEBULAOPS_UI_KIT_VERSION = '2.0.0-v24.1';

export const NEBULAOPS_UI_TOKENS = {
  density: {
    compactCardPadding: '12px',
    compactRowHeight: '36px',
    stickyActionBarTop: '0px'
  },
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
    card: '18px',
    pill: '999px',
    modal: '24px'
  }
} as const;
