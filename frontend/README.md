# Ledger AI frontend

React and TypeScript interface for the Spring AI chat and invoice-analysis
endpoints.

## Start locally

Run the Spring Boot application on port 8080. Then:

```bash
npm install
npm start
```

Vite serves the frontend on port 5173 by default and proxies requests under
`/ai` to `http://localhost:8080`.

## Checks

```bash
npm test
npm run lint
npm run build
```
