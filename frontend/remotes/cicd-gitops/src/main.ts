import { AppComponent } from './app/app.component';
import { bootstrapNebulaOpsMfe } from '@nebulaops/mfe-core';

bootstrapNebulaOpsMfe({
  tagName: 'nebulaops-mfe-cicd-gitops',
  component: AppComponent,
}).catch(error => console.error('NebulaOps MFE bootstrap failed:', error));
