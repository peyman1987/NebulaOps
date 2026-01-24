import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  standalone: true,
  template: '<div class="live-only-source">Progressive Delivery Center uses runtime APIs only. The generated runtime bundle reads /api/progressive-delivery/**.</div>',
  styles: ['.live-only-source{padding:24px;color:#eaf4ff;background:#050816;font-family:Inter,system-ui,sans-serif}']
})
export class AppComponent {}
