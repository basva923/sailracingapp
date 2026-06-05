# Development guide

This guide helps contributors and agents get productive quickly.

## Prerequisites

- Node.js and npm
- Chrome or Chromium for Karma tests

## Install dependencies

```sh
npm ci
```

Use the lockfile for reproducible installs.

## Run locally

```sh
npm start
```

The app is served by Angular CLI using the development configuration.

## Build

```sh
npm run build
```

This creates a production build in `dist/sailracingapp`.

For the GitHub Pages deployment build, run:

```sh
npm run build-github-page
```

## Test

Run the test suite once in headless mode:

```sh
npm test -- --watch=false --browsers=ChromeHeadless
```

Run interactive/watch tests with:

```sh
npm test
```

## Project structure

- `src/main.ts` bootstraps the Angular application.
- `src/app/app.config.ts` configures app-wide providers.
- `src/app/app.routes.ts` defines routes.
- `src/app/*/*.component.ts` files contain feature components.
- `src/app/util/` contains shared helpers.
- Component tests live next to implementation files as `*.spec.ts`.

## Style and conventions

- Use TypeScript strictness already configured in `tsconfig.json`.
- Use two-space indentation.
- Prefer single quotes in TypeScript.
- Keep component templates, styles, and tests colocated with components.
- Do not add new dependencies unless they are needed for the requested change.
- Do not commit generated build artifacts.
