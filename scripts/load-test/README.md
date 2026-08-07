# TV4 load-test and benchmark

Prerequisites: Docker Desktop is running, the full Compose stack can start, Node.js 18+ and k6 are installed, and at least one exam is `PUBLISHED`.

Run the protected Gateway profile only:

```powershell
.\scripts\load-test\run-tv4-benchmark.ps1 -Profile protected -ExamId 1
```

Run the reproducible baseline and protected comparison:

```powershell
.\scripts\load-test\run-tv4-benchmark.ps1 -Profile both -ExamId 1
```

The runner creates fresh submissions before every steady and spike run. It writes k6 summaries and system evidence under `scripts/load-test/results/`; these generated artifacts are intentionally ignored by Git. Copy the measured values into `docs/tv4-load-test-report.md` for the final presentation.

The baseline Compose override only disables the Gateway protection for measurement. The normal `docker-compose.yml` remains the protected configuration and is restored by the runner when it finishes.
