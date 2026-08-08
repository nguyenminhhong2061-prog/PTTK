const fs = require('fs');
const path = require('path');

const baseUrl = (process.env.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const requestedExamId = process.env.EXAM_ID;
const studentCount = Number(process.env.STUDENT_COUNT || 200);
const runId = process.env.RUN_ID || new Date().toISOString().replace(/[^0-9]/g, '');

async function requestJson(url, options) {
  const response = await fetch(url, options);
  const text = await response.text();
  let body;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    throw new Error(`${options?.method || 'GET'} ${url} returned non-JSON (${response.status}): ${text}`);
  }
  if (!response.ok) {
    throw new Error(`${options?.method || 'GET'} ${url} failed (${response.status}): ${text}`);
  }
  return body;
}

async function resolveExamId() {
  if (requestedExamId) return Number(requestedExamId);

  const body = await requestJson(`${baseUrl}/api/exams?page=1&limit=100&status=PUBLISHED`);
  const exams = body?.data || [];
  if (!exams.length) {
    throw new Error('No PUBLISHED exam found. Publish an exam or provide EXAM_ID.');
  }
  return exams[0].id;
}

async function main() {
  if (!Number.isInteger(studentCount) || studentCount < 1) {
    throw new Error('STUDENT_COUNT must be a positive integer.');
  }

  const examId = await resolveExamId();
  const submissions = new Array(studentCount);
  const workers = Math.min(20, studentCount);

  await Promise.all(Array.from({length: workers}, async (_, worker) => {
    for (let index = worker + 1; index <= studentCount; index += workers) {
      const studentId = `load-${runId}-${String(index).padStart(3, '0')}`;
      const body = await requestJson(`${baseUrl}/api/submissions`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({examId, studentId}),
      });

      const submissionId = body?.data?.submissionId;
      if (!submissionId) {
        throw new Error(`Missing submissionId for ${studentId}: ${JSON.stringify(body)}`);
      }
      submissions[index - 1] = {studentId, submissionId, examId};
    }
  }));

  const output = path.join(__dirname, 'fixtures', 'submissions.json');
  fs.mkdirSync(path.dirname(output), {recursive: true});
  fs.writeFileSync(output, `${JSON.stringify(submissions, null, 2)}\n`, 'utf8');
  process.stdout.write(`Created ${submissions.length} submissions for exam ${examId}: ${output}\n`);
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message}\n`);
  process.exitCode = 1;
});
