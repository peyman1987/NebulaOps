import { AppComponent } from './app/app.component';
import { bootstrapNebulaOpsMfe } from '@nebulaops/mfe-core';

bootstrapNebulaOpsMfe({
  tagName: 'nebulaops-mfe-devsecops',
  component: AppComponent,
}).catch(error => console.error('NebulaOps MFE bootstrap failed:', error));
