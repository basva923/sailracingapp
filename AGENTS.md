# Agent onboarding

Use this file as the quick-start guide for coding agents working in this repository.

## Project overview

Sailracingapp is an Angular 21 single-page application for sailing race assistance. Planned and existing feature areas include a start timer, wind information, heading, speed, start line tooling, maps, and racing analytics.

## Tech stack

- Angular 21
- TypeScript 5.9 with strict compiler settings
- Bootstrap 5
- RxJS
- Jasmine/Karma for tests
- Angular service worker for production PWA builds

## Repository layout

- `src/app/` - Angular application code.
- `src/app/home/` - Home page component.
- `src/app/timer/` - Race timer component.
- `src/app/wind/` - Wind information component.
- `src/app/util/` - Shared utility functions and unit conversion helpers.
- `src/assets/` - Static assets and PWA icons.
- `src/styles.css` - Global styles.
- `angular.json` - Angular CLI build and test configuration.
- `.github/workflows/build-and-deploy.yaml` - GitHub Pages build/deploy workflow.

## Setup

Install dependencies from the lockfile:

```sh
npm ci
```

## Common commands

```sh
npm start
```

Runs the local development server with `ng serve`.

```sh
npm run build
```

Builds the production application.

```sh
npm run build-github-page
```

Builds for GitHub Pages with the `/sailracingapp/` base href.

```sh
npm test -- --watch=false --browsers=ChromeHeadless
```

Runs the existing Karma/Jasmine tests once in a headless browser.

## Development notes

- There is no lint script configured.
- Follow `.editorconfig`: two-space indentation, UTF-8, final newline, and single quotes in TypeScript.
- Keep changes small and scoped to the requested feature or fix.
- Prefer Angular CLI conventions for components, services, and tests.
- Add or update nearby `*.spec.ts` tests when changing application logic.
- Do not commit generated build output from `dist/`.

## Known baseline

At the time this guide was added:

- `npm run build` succeeds.
- Headless tests run with the command above, but the suite has an existing failing `AppComponent` assertion and geolocation-related browser output.
