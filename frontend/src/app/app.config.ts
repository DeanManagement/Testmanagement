import { ApplicationConfig, inject, provideAppInitializer, provideZonelessChangeDetection, isDevMode } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideStoreDevtools } from '@ngrx/store-devtools';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import localeDe from '@angular/common/locales/de';

registerLocaleData(localeDe);

import { routes } from './app.routes';
import { ThemeService } from './core/services/theme.service';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { authReducer } from './store/auth/auth.reducer';
import { projectReducer } from './store/project/project.reducer';
import { ProjectEffects } from './store/project/project.effects';
import { testCaseReducer } from './store/test-case/test-case.reducer';
import { TestCaseEffects } from './store/test-case/test-case.effects';
import { testSuiteReducer } from './store/test-suite/test-suite.reducer';
import { TestSuiteEffects } from './store/test-suite/test-suite.effects';
import { testRunReducer } from './store/test-run/test-run.reducer';
import { TestRunEffects } from './store/test-run/test-run.effects';
import { apiKeyReducer } from './store/api-key/api-key.reducer';
import { ApiKeyEffects } from './store/api-key/api-key.effects';
import { userReducer } from './store/user/user.reducer';
import { UserEffects } from './store/user/user.effects';
import { commentReducer } from './store/comment/comment.reducer';
import { CommentEffects } from './store/comment/comment.effects';
import { testPlanReducer } from './store/test-plan/test-plan.reducer';
import { TestPlanEffects } from './store/test-plan/test-plan.effects';
import { bugReportReducer } from './store/bug-report/bug-report.reducer';
import { BugReportEffects } from './store/bug-report/bug-report.effects';
import { testCaseFolderReducer } from './store/test-case-folder/test-case-folder.reducer';
import { TestCaseFolderEffects } from './store/test-case-folder/test-case-folder.effects';
import { watcherReducer } from './store/watcher/watcher.reducer';
import { WatcherEffects } from './store/watcher/watcher.effects';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    // Apply the stored theme before the first render so there is no light-mode flash.
    provideAppInitializer(() => inject(ThemeService).init()),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    provideAnimationsAsync(),
    provideStore({
      auth: authReducer,
      projects: projectReducer,
      testCases: testCaseReducer,
      testSuites: testSuiteReducer,
      testRuns: testRunReducer,
      apiKeys: apiKeyReducer,
      users: userReducer,
      comments: commentReducer,
      testPlans: testPlanReducer,
      bugReports: bugReportReducer,
      testCaseFolders: testCaseFolderReducer,
      watchers: watcherReducer,
    }),
    provideEffects(ProjectEffects, TestCaseEffects, TestCaseFolderEffects, TestSuiteEffects, TestRunEffects, ApiKeyEffects, UserEffects, CommentEffects, TestPlanEffects, BugReportEffects, WatcherEffects),
    // Dev only: even logOnly devtools expose the full state tree to the extension in prod.
    ...(isDevMode() ? [provideStoreDevtools({ maxAge: 25 })] : []),
    provideTranslateService({
      fallbackLang: 'en',
    }),
    provideTranslateHttpLoader(),
  ],
};
