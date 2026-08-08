import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { BuildServerApiService } from '../../core/services/build-server-api.service';
import { ProjectApiService } from '../../core/services/project-api.service';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../shared/components/confirm-dialog/confirm-dialog.component';
import {
  BuildServerConfig,
  BuildServerProviderType,
  BuildWorkflow,
  DiscoveredWorkflow,
  REPO_REF_HINT,
  SaveBuildServerConfigRequest,
  SaveBuildWorkflowRequest,
} from '../../shared/models/build-server.model';
import { Project } from '../../shared/models/project.model';
import { LocalizedDatePipe } from '../../shared/pipes/localized-date.pipe';

/**
 * Instance-wide build-server administration (PRD-024 §3.5): register servers, define workflows —
 * via provider discovery where available, manually otherwise — and assign them to projects.
 * Reached from the global settings navigation; the backend rejects non-admins.
 *
 * <p>The stored API token is never returned by the API, so the field always starts empty and an
 * empty value on save means "keep the existing token".
 */
@Component({
  selector: 'app-build-server-settings',
  standalone: true,
  imports: [
    FormsModule,
    LocalizedDatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './build-server-settings.component.html',
  styleUrl: './build-server-settings.component.scss',
})
export class BuildServerSettingsComponent implements OnInit {
  private readonly api = inject(BuildServerApiService);
  private readonly projectApi = inject(ProjectApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  loading = true;
  providers: BuildServerProviderType[] = [];
  servers: BuildServerConfig[] = [];
  projects: Project[] = [];
  workflowsByServer: Record<string, BuildWorkflow[]> = {};
  expandedServerId: string | null = null;

  // Server form
  serverFormOpen = false;
  editingServer: BuildServerConfig | null = null;
  sName = '';
  sProvider: BuildServerProviderType = 'GITLAB_CI';
  sBaseUrl = '';
  sToken = '';
  sActive = true;
  savingServer = false;
  testingServerId: string | null = null;

  // Workflow form
  workflowFormServerId: string | null = null;
  editingWorkflow: BuildWorkflow | null = null;
  wName = '';
  wRepoRef = '';
  wWorkflowRef = '';
  wDefaultRef = '';
  wParamsText = '';
  wActive = true;
  savingWorkflow = false;

  // Discovery
  discovering = false;
  discoverySupported: boolean | null = null;
  discovered: DiscoveredWorkflow[] = [];

  // Assignment edits, keyed by workflow id
  assignments: Record<string, string[]> = {};
  savingAssignmentId: string | null = null;

  ngOnInit(): void {
    this.api.getSupportedProviders()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (providers) => {
          this.providers = providers;
          if (providers.length > 0 && !providers.includes(this.sProvider)) {
            this.sProvider = providers[0];
          }
          this.cdr.markForCheck();
        },
        error: () => undefined,
      });
    this.projectApi.getAll()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (projects) => {
          this.projects = projects;
          this.cdr.markForCheck();
        },
        error: () => undefined,
      });
    this.loadServers();
  }

  workflowsFor(serverId: string): BuildWorkflow[] {
    return this.workflowsByServer[serverId] ?? [];
  }

  get repoRefHint(): string {
    const server = this.workflowFormServerId
      ? this.servers.find((s) => s.id === this.workflowFormServerId)
      : null;
    return server ? REPO_REF_HINT[server.provider] : '';
  }

  // ---- Servers ----------------------------------------------------------

  loadServers(): void {
    this.loading = true;
    this.api.getServers()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (servers) => {
          this.servers = servers;
          this.loading = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.loading = false;
          this.cdr.markForCheck();
        },
      });
  }

  openServerForm(server: BuildServerConfig | null): void {
    this.serverFormOpen = true;
    this.editingServer = server;
    this.sName = server?.name ?? '';
    this.sProvider = server?.provider ?? (this.providers[0] ?? 'GITLAB_CI');
    this.sBaseUrl = server?.baseUrl ?? '';
    this.sToken = '';
    this.sActive = server?.active ?? true;
  }

  closeServerForm(): void {
    this.serverFormOpen = false;
    this.editingServer = null;
  }

  saveServer(): void {
    const request: SaveBuildServerConfigRequest = {
      name: this.sName.trim(),
      provider: this.sProvider,
      baseUrl: this.sBaseUrl.trim(),
      apiToken: this.sToken || undefined,
      active: this.sActive,
    };
    this.savingServer = true;
    const call = this.editingServer
      ? this.api.updateServer(this.editingServer.id, request)
      : this.api.createServer(request);
    call.pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.savingServer = false;
        this.closeServerForm();
        this.snackBar.open(this.translate.instant('buildServers.saved'), undefined, { duration: 3000 });
        this.loadServers();
      },
      error: (err) => {
        this.savingServer = false;
        this.cdr.markForCheck();
        this.snackBar.open(
          err?.error?.message ?? this.translate.instant('common.saveError'),
          undefined, { duration: 5000 });
      },
    });
  }

  deleteServer(server: BuildServerConfig): void {
    const data: ConfirmDialogData = {
      titleKey: 'buildServers.deleteTitle',
      messageKey: 'buildServers.deleteMessage',
      messageParams: { name: server.name },
      danger: true,
    };
    this.dialog.open(ConfirmDialogComponent, { data }).afterClosed()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.deleteServer(server.id)
          .pipe(take(1), takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: () => this.loadServers(),
            error: () => this.snackBar.open(
              this.translate.instant('common.deleteError'), undefined, { duration: 5000 }),
          });
      });
  }

  testServer(server: BuildServerConfig): void {
    this.testingServerId = server.id;
    this.api.testConnection(server.id)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.testingServerId = null;
          this.snackBar.open(
            this.translate.instant('buildServers.testOk'), undefined, { duration: 3000 });
          this.loadServers();
        },
        error: (err) => {
          this.testingServerId = null;
          this.cdr.markForCheck();
          this.snackBar.open(
            err?.error?.message ?? this.translate.instant('buildServers.testFailed'),
            undefined, { duration: 5000 });
          this.loadServers();
        },
      });
  }

  toggleExpand(server: BuildServerConfig): void {
    if (this.expandedServerId === server.id) {
      this.expandedServerId = null;
      return;
    }
    this.expandedServerId = server.id;
    this.loadWorkflows(server.id);
  }

  // ---- Workflows --------------------------------------------------------

  private loadWorkflows(serverId: string): void {
    this.api.getWorkflows(serverId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (workflows) => {
          this.workflowsByServer = { ...this.workflowsByServer, [serverId]: workflows };
          for (const workflow of workflows) {
            this.assignments[workflow.id] = [...workflow.projectIds];
          }
          this.cdr.markForCheck();
        },
        error: () => undefined,
      });
  }

  openWorkflowForm(serverId: string, workflow: BuildWorkflow | null): void {
    this.workflowFormServerId = serverId;
    this.editingWorkflow = workflow;
    this.wName = workflow?.name ?? '';
    this.wRepoRef = workflow?.repoRef ?? '';
    this.wWorkflowRef = workflow?.workflowRef ?? '';
    this.wDefaultRef = workflow?.defaultRef ?? '';
    this.wParamsText = Object.entries(workflow?.defaultParameters ?? {})
      .map(([key, value]) => `${key}=${value}`)
      .join('\n');
    this.wActive = workflow?.active ?? true;
    this.discovered = [];
    this.discoverySupported = null;
  }

  closeWorkflowForm(): void {
    this.workflowFormServerId = null;
    this.editingWorkflow = null;
    this.discovered = [];
    this.discoverySupported = null;
  }

  discover(): void {
    if (!this.workflowFormServerId) {
      return;
    }
    this.discovering = true;
    this.api.discoverWorkflows(this.workflowFormServerId, this.wRepoRef.trim() || null)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.discovering = false;
          this.discoverySupported = result.supported;
          this.discovered = result.workflows;
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.discovering = false;
          this.cdr.markForCheck();
          this.snackBar.open(
            err?.error?.message ?? this.translate.instant('buildServers.discoverFailed'),
            undefined, { duration: 5000 });
        },
      });
  }

  pickDiscovered(workflow: DiscoveredWorkflow): void {
    if (!this.wName) {
      this.wName = workflow.name;
    }
    this.wRepoRef = workflow.repoRef ?? this.wRepoRef;
    this.wWorkflowRef = workflow.workflowRef ?? '';
    if (workflow.defaultRef) {
      this.wDefaultRef = workflow.defaultRef;
    }
    this.discovered = [];
  }

  saveWorkflow(): void {
    if (!this.workflowFormServerId) {
      return;
    }
    const serverId = this.workflowFormServerId;
    const request: SaveBuildWorkflowRequest = {
      name: this.wName.trim(),
      repoRef: this.wRepoRef.trim(),
      workflowRef: this.wWorkflowRef.trim() || null,
      defaultRef: this.wDefaultRef.trim() || null,
      defaultParameters: this.parseParams(this.wParamsText),
      active: this.wActive,
    };
    this.savingWorkflow = true;
    const call = this.editingWorkflow
      ? this.api.updateWorkflow(this.editingWorkflow.id, request)
      : this.api.createWorkflow(serverId, request);
    call.pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.savingWorkflow = false;
        this.closeWorkflowForm();
        this.loadWorkflows(serverId);
      },
      error: (err) => {
        this.savingWorkflow = false;
        this.cdr.markForCheck();
        this.snackBar.open(
          err?.error?.message ?? this.translate.instant('common.saveError'),
          undefined, { duration: 5000 });
      },
    });
  }

  deleteWorkflow(serverId: string, workflow: BuildWorkflow): void {
    const data: ConfirmDialogData = {
      titleKey: 'buildServers.deleteWorkflowTitle',
      messageKey: 'buildServers.deleteWorkflowMessage',
      messageParams: { name: workflow.name },
      danger: true,
    };
    this.dialog.open(ConfirmDialogComponent, { data }).afterClosed()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.deleteWorkflow(workflow.id)
          .pipe(take(1), takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: () => this.loadWorkflows(serverId),
            error: () => this.snackBar.open(
              this.translate.instant('common.deleteError'), undefined, { duration: 5000 }),
          });
      });
  }

  assignmentChanged(workflow: BuildWorkflow): boolean {
    const current = [...workflow.projectIds].sort();
    const edited = [...(this.assignments[workflow.id] ?? [])].sort();
    return current.length !== edited.length || current.some((id, i) => id !== edited[i]);
  }

  saveAssignments(serverId: string, workflow: BuildWorkflow): void {
    this.savingAssignmentId = workflow.id;
    this.api.assignProjects(workflow.id, this.assignments[workflow.id] ?? [])
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.savingAssignmentId = null;
          this.snackBar.open(
            this.translate.instant('buildServers.assignmentsSaved'), undefined, { duration: 3000 });
          this.loadWorkflows(serverId);
        },
        error: (err) => {
          this.savingAssignmentId = null;
          this.cdr.markForCheck();
          this.snackBar.open(
            err?.error?.message ?? this.translate.instant('common.saveError'),
            undefined, { duration: 5000 });
        },
      });
  }

  private parseParams(text: string): Record<string, string> {
    const parameters: Record<string, string> = {};
    for (const line of text.split('\n')) {
      const trimmed = line.trim();
      if (!trimmed) {
        continue;
      }
      const separator = trimmed.indexOf('=');
      if (separator > 0) {
        parameters[trimmed.slice(0, separator).trim()] = trimmed.slice(separator + 1).trim();
      }
    }
    return parameters;
  }
}
