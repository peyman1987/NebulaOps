import { AppComponent } from './app/app.component';
import { bootstrapNebulaOpsMfe } from '@nebulaops/mfe-core';

bootstrapNebulaOpsMfe({
  tagName: 'nebulaops-mfe-observability',
  component: AppComponent,
}).catch(error => console.error('NebulaOps MFE bootstrap failed:', error));
