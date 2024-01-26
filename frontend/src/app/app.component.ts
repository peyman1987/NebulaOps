import {Component, computed, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {CdkDragDrop, DragDropModule} from '@angular/cdk/drag-drop';

type Status = 'TODO' | 'IN_PROGRESS' | 'REVIEW' | 'DONE';

interface Task {
    id: string;
    title: string;
    owner: string;
    priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
    status: Status;
}

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [CommonModule, DragDropModule],
    templateUrl: './app.component.html',
    styleUrl: './app.component.css'
})
export class AppComponent {
    readonly tasks = signal<Task[]>([
        {id: 'T-101', title: 'MongoDB aggregate workload dashboard', owner: 'Peyman', priority: 'HIGH', status: 'TODO'},
        {
            id: 'T-102',
            title: 'Kafka task event contract',
            owner: 'Platform',
            priority: 'CRITICAL',
            status: 'IN_PROGRESS'
        },
        {id: 'T-103', title: 'Angular CDK Kanban refactor', owner: 'Frontend', priority: 'HIGH', status: 'REVIEW'},
        {id: 'T-104', title: 'Gateway smoke tests', owner: 'DevOps', priority: 'MEDIUM', status: 'DONE'}
    ]);
    readonly columns: Status[] = ['TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'];
    total = computed(() => this.tasks().length);
    done = computed(() => this.tasks().filter(t => t.status === 'DONE').length);
    critical = computed(() => this.tasks().filter(t => t.priority === 'CRITICAL').length);

    columnTasks(status: Status) {
        return this.tasks().filter(t => t.status === status);
    }

    drop(event: CdkDragDrop<Task[]>, status: Status) {
        const current = this.tasks();
        const item = event.previousContainer.data[event.previousIndex];
        if (!item) return;
        this.tasks.set(current.map(t => t.id === item.id ? {...t, status} : t));
    }
}
