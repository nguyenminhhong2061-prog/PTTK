import {
  AlignmentType, BorderStyle, Document, Footer, Header, Packer, PageNumber,
  Paragraph, ShadingType, Table, TableCell, TableRow, TextRun, WidthType,
} from 'docx';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';

const out = resolve('docs/TV4_Test_Report_Template.docx');
const W = 9360;
const BLUE = '2E74B5';
const DARK = '1F4D78';
const INK = '0B2545';
const HEADER = 'E8EEF5';
const CALLOUT = 'F4F6F9';
const CAUTION = 'FFF4CC';
const WHITE = 'FFFFFF';

const borders = { top: { style: BorderStyle.SINGLE, size: 4, color: 'B7C3D0' }, bottom: { style: BorderStyle.SINGLE, size: 4, color: 'B7C3D0' }, left: { style: BorderStyle.SINGLE, size: 4, color: 'B7C3D0' }, right: { style: BorderStyle.SINGLE, size: 4, color: 'B7C3D0' } };
const page = { width: 12240, height: 15840, margin: { top: 1440, right: 1440, bottom: 1440, left: 1440, header: 709, footer: 709 } };

function run(text, opts = {}) {
  return new TextRun({ text, font: opts.font || 'Calibri', size: opts.size || 22, bold: opts.bold, italics: opts.italics, color: opts.color || INK, break: opts.break, allCaps: opts.allCaps });
}
function para(text = '', opts = {}) {
  return new Paragraph({
    children: Array.isArray(text) ? text : [run(text, opts)],
    alignment: opts.align || AlignmentType.LEFT,
    spacing: { before: opts.before ?? 0, after: opts.after ?? 120, line: opts.line ?? 300 },
    keepNext: opts.keepNext,
    pageBreakBefore: opts.pageBreakBefore,
  });
}
function heading(text, level = 1, opts = {}) {
  const size = level === 1 ? 32 : level === 2 ? 26 : 24;
  return new Paragraph({ children: [run(text, { size, bold: true, color: level === 3 ? DARK : BLUE })], spacing: { before: opts.before ?? (level === 1 ? 360 : level === 2 ? 280 : 200), after: opts.after ?? (level === 1 ? 200 : level === 2 ? 140 : 100) }, keepNext: true, pageBreakBefore: opts.pageBreakBefore });
}
function cell(content, width, opts = {}) {
  const children = Array.isArray(content) ? content : [typeof content === 'string' ? para(content, { size: opts.size || 18, after: 0, line: 240, bold: opts.bold, color: opts.color }) : content];
  return new TableCell({ children, width: { size: width, type: WidthType.DXA }, shading: opts.fill ? { type: ShadingType.CLEAR, fill: opts.fill } : undefined, borders, margins: { top: 80, bottom: 80, left: 120, right: 120 }, verticalAlign: opts.verticalAlign });
}
function table(rows, widths, opts = {}) {
  return new Table({ rows: rows.map((row, i) => new TableRow({ children: row.map((value, j) => cell(value, widths[j], { fill: i === 0 && opts.header !== false ? HEADER : opts.fill, bold: i === 0 && opts.header !== false, size: opts.size || 18 })) })), width: { size: W, type: WidthType.DXA }, columnWidths: widths, borders, layout: 'fixed', indent: { size: 120, type: WidthType.DXA } });
}
function code(lines) {
  return new Table({ rows: [new TableRow({ children: [new TableCell({ children: lines.map((line) => new Paragraph({ children: [run(line, { font: 'Consolas', size: 16, color: '243447' })], spacing: { after: 0, line: 210 } })), width: { size: W, type: WidthType.DXA }, shading: { type: ShadingType.CLEAR, fill: CALLOUT }, margins: { top: 100, bottom: 100, left: 140, right: 140 }, borders })] })], width: { size: W, type: WidthType.DXA }, columnWidths: [W], layout: 'fixed', indent: { size: 120, type: WidthType.DXA } });
}
function bullets(items) { return items.map((x) => new Paragraph({ children: [run(x, { size: 22 })], bullet: { level: 0 }, spacing: { after: 80, line: 300 } })); }
function callout(title, body, caution = false) {
  return new Table({ rows: [new TableRow({ children: [cell([para([run(title, { size: 20, bold: true, color: caution ? '7A5A00' : DARK })], { after: 60, line: 240 }), para(body, { size: 19, after: 0, line: 250 })], W, { fill: caution ? CAUTION : CALLOUT })] })], width: { size: W, type: WidthType.DXA }, columnWidths: [W], layout: 'fixed', indent: { size: 120, type: WidthType.DXA } });
}
function tc(id, name, actions, expected, note = '') {
  return [
    heading(`${id} — ${name}`, 2),
    table([['Chuẩn bị', 'Thao tác / lệnh'], ['[Điền dữ liệu, trạng thái dịch vụ, RUN_ID]', actions]], [2700, 6660]),
    table([['Kết quả mong đợi', 'Kết quả thực tế (tự điền)'], [expected, '\n\n\n']], [4680, 4680]),
    table([['Trạng thái', 'Bằng chứng / ghi chú'], ['☐ PASS   ☐ FAIL   ☐ BLOCKED   ☐ N/A', note ? `${note}\n\nĐường dẫn log, ảnh chụp, summary.json: ______________________________` : 'Đường dẫn log, ảnh chụp, summary.json: ______________________________']], [2500, 6860]),
    para('', { after: 80 }),
  ];
}

const children = [];
children.push(
  para('MẪU BÁO CÁO KIỂM THỬ', { size: 20, bold: true, color: DARK, after: 40, allCaps: true }),
  para('Nhiệm vụ Thành viên 4', { size: 42, bold: true, color: INK, after: 80, line: 420 }),
  para('Consumer Statistics · Gateway protection · Kiểm thử tải 200 học sinh', { size: 25, color: DARK, after: 320 }),
  table([
    ['Thông tin', 'Giá trị cần điền'],
    ['Nhóm / lớp', '____________________________________________________________'],
    ['Người kiểm thử', '____________________________________________________________'],
    ['Ngày, giờ kiểm thử', '____________________________________________________________'],
    ['Nhánh / commit Git', '____________________________________________________________'],
    ['Môi trường', 'Windows / Docker Desktop / localhost:8080 / khác: __________________'],
    ['Exam ID dùng cho load test', '____________________________________________________________'],
  ], [2700, 6660]),
  para('', { after: 200 }),
  callout('Cách dùng mẫu này', 'Chạy từng mục theo thứ tự. Điền kết quả thực tế ngay sau khi chạy, lưu log/ảnh chụp/summary.json vào thư mục evidence riêng, rồi dán đường dẫn ở cột Bằng chứng. Chỉ đánh PASS khi kết quả thực tế khớp tiêu chí mong đợi.'),
  heading('1. Phạm vi và tiêu chí nghiệm thu', 1),
  para('Mẫu này bao quát kiểm thử đơn vị, Gateway, Outbox → RabbitMQ → Statistics, sáu demo tích hợp và tải mô phỏng 200 học sinh.'),
  ...bullets([
    'Mã phản hồi hợp lệ ở tải Gateway: 200, 409, 429, 503, 504. Không chấp nhận 5xx ngoài 503/504, lỗi HTML, hoặc Gateway/backend bị crash.',
    'POST /submit không tự retry ở Gateway; khi 504, chỉ kiểm tra trạng thái submission thay vì gửi lại POST.',
    'Outbox không mất event khi RabbitMQ dừng; consumer chỉ xử lý một event hợp lệ một lần; event lỗi đi DLQ sau retry.',
    'Mỗi lượt load phải tạo fixture mới, RUN_ID mới và summary.json mới. Không dùng kết quả cũ làm số liệu chính thức.',
  ]),
  heading('2. Chuẩn bị môi trường', 1),
  para('Điền phiên bản thực tế trước khi chạy. Những lệnh dưới đây chạy từ D:\\PROJECT\\PTTK.'),
  code([
    'cd D:\\PROJECT\\PTTK',
    'docker version',
    "& 'C:\\Program Files\\k6\\k6.exe' version",
    'node --version',
    'docker compose config --quiet',
    'docker compose up -d --build',
    'docker compose ps',
  ]),
  table([
    ['Hạng mục', 'Kết quả thực tế (tự điền)'],
    ['Docker Desktop / Docker daemon', 'Phiên bản: ____________________  ☐ hoạt động  ☐ lỗi'],
    ['k6', 'Phiên bản: ____________________  Đường dẫn: ______________________________'],
    ['Node.js / Java / Maven', 'Node: __________  Java: __________  Maven: __________'],
    ['docker compose config --quiet', '☐ PASS  ☐ FAIL  Log: _______________________________________________'],
    ['Dịch vụ sau docker compose ps', '☐ tất cả running/healthy  ☐ lỗi: __________________________________'],
  ], [3000, 6360]),
  callout('Lưu ý Testcontainers', "Nếu test Statistics không kết nối được Docker trên Windows, đặt biến môi trường trước khi chạy: $env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'", true),
  heading('3. Kiểm thử tự động', 1),
  ...tc('UNIT-STAT-01', 'Statistics Service', "cd services\\statistics-service\n& 'C:\\Program Files\\Git\\bin\\bash.exe' -lc './mvnw test'", 'Toàn bộ test xanh (mốc hiện hành: 11 test).'),
  ...tc('UNIT-SUB-01', 'Submission Service', "cd services\\submission-service\n& 'C:\\Program Files\\Git\\bin\\bash.exe' -lc './mvnw test'", 'Toàn bộ test xanh (mốc hiện hành: 9 test). Giữ nguyên bộ test TV3; test TV4 là bổ sung.'),
  heading('4. Gateway — cấu hình và lỗi có kiểm soát', 1),
  para('Harness sẽ lần lượt kiểm tra 429, 502 và 504, đồng thời xác minh POST timeout chỉ chạm upstream đúng một lần.'),
  code([
    'cd D:\\PROJECT\\PTTK',
    'docker compose exec -T gateway nginx -t',
    '.\\gateway\\tests\\verify-gateway.ps1',
  ]),
  ...tc('GATE-01', 'Kiểm tra cấu hình Nginx', 'docker compose exec -T gateway nginx -t', 'Nginx báo syntax is ok và test is successful.'),
  ...tc('GATE-02', 'Rate limit 429', '.\\gateway\\tests\\verify-gateway.ps1', 'Khi vượt giới hạn, nhận 429 body JSON và header Retry-After. Gateway vẫn hoạt động.'),
  ...tc('GATE-03', 'Bad Gateway 502', '.\\gateway\\tests\\verify-gateway.ps1', 'Khi submission-service dừng, nhận 502 body JSON có kiểm soát.'),
  ...tc('GATE-04', 'Timeout 504, không retry POST', '.\\gateway\\tests\\verify-gateway.ps1', 'Nhận 504 body JSON. slow-upstream ghi đúng 1 request, chứng minh Nginx không retry POST /submit.'),
  heading('5. Demo tích hợp', 1),
  para('Các kịch bản sau là bằng chứng end-to-end. Nếu một năng lực thuộc thành viên khác chưa được tích hợp, ghi N/A kèm lý do thay vì đánh PASS.'),
  ...tc('E2E-01', 'Outbox → RabbitMQ → Statistics', 'Tạo một submission mới, submit thành công; đợi consumer xử lý rồi truy vấn Outbox, processed_events và RabbitMQ.', 'Outbox chuyển PENDING → SENT; processed_events tăng 1; queue chính và DLQ không tăng bất thường.'),
  ...tc('E2E-02', 'Idempotency event / submission', 'Gửi lại cùng submission hoặc cùng eventId theo kịch bản dự án; kiểm tra processed_events.', 'Event hợp lệ chỉ được xử lý một lần. Bản sao được ACK/bỏ qua; nếu submit trùng bị từ chối thì 409 là hợp lệ.'),
  ...tc('E2E-03', 'RabbitMQ dừng rồi khôi phục', 'docker compose stop rabbitmq\n# tạo và submit 1 submission\ndocker compose up -d rabbitmq\n# đợi relay/consumer xử lý', 'Khi RabbitMQ dừng, event không mất (Outbox PENDING). Khi khôi phục, Outbox SENT và Statistics xử lý đúng một lần.'),
  ...tc('E2E-04', 'Statistics restart', 'docker compose stop statistics-service\n# tạo và submit 1 submission\ndocker compose up -d statistics-service\n# theo dõi queue và processed_events', 'Message tồn đọng được xử lý sau khi consumer khởi động lại; không mất và không xử lý trùng.'),
  ...tc('E2E-05', 'Saga rollback', 'Dừng exam-service hoặc tạo điều kiện lỗi theo luồng Saga, sau đó thực hiện thao tác tạo/nộp bài.', 'Không còn dữ liệu submit dang dở hoặc trạng thái không nhất quán sau lỗi; ghi rõ thao tác kích hoạt lỗi.'),
  ...tc('E2E-06', 'Circuit Breaker', 'Kích hoạt lỗi phụ thuộc theo tích hợp TV2, sau đó gọi lại endpoint liên quan.', 'Circuit Breaker mở/chuyển trạng thái theo cấu hình và có phản hồi có kiểm soát. Nếu chưa tích hợp: đánh N/A + lý do.'),
  heading('6. Truy vấn xác minh dữ liệu', 1),
  para('Chạy sau mỗi demo hoặc lượt tải. Dán output đã lưu vào đường dẫn bằng chứng của test case tương ứng.'),
  code([
    'docker compose exec -T submission-db mysql -uroot -pchangeme submission_db -e "SELECT status, COUNT(*) AS total FROM outbox_events GROUP BY status;"',
    'docker compose exec -T statistics-db mysql -uroot -pchangeme statistics_db -e "SELECT COUNT(*) AS processed_events FROM processed_events;"',
    'docker compose exec -T rabbitmq rabbitmqctl list_queues name messages',
  ]),
  table([
    ['Thời điểm / RUN_ID', 'Outbox PENDING', 'Outbox SENT', 'processed_events', 'Queue chính / DLQ', 'Ghi chú'],
    ['________________', '________', '________', '________', '________________', '________________________________'],
    ['________________', '________', '________', '________', '________________', '________________________________'],
    ['________________', '________', '________', '________', '________________', '________________________________'],
  ], [1700, 1250, 1150, 1450, 1850, 1960]),
  heading('7. Load test — mô phỏng 200 học sinh', 1),
  callout('Quy tắc dữ liệu sạch', 'Mỗi profile và mỗi lần chạy phải sinh fixture 200 submission mới bằng RUN_ID mới. Không tái dùng fixtures/submissions.json hoặc summary.json cũ làm số liệu cho lần chạy mới.', true),
  heading('7.1 Tạo fixture mới', 2),
  code([
    "$env:BASE_URL='http://localhost:8080'",
    "$env:EXAM_ID='1'                 # thay bằng exam đã PUBLISHED",
    "$env:STUDENT_COUNT='200'",
    '$env:RUN_ID="manual-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"',
    'node .\\scripts\\load-test\\prepare-data.js',
  ]),
  table([
    ['Lượt fixture', 'EXAM_ID', 'RUN_ID', 'Số submission tạo được', 'Kết quả / bằng chứng'],
    ['Steady', '________', '________________________', '________ / 200', '________________________________________'],
    ['Spike', '________', '________________________', '________ / 200', '________________________________________'],
    ['Baseline (nếu chạy)', '________', '________________________', '________ / 200', '________________________________________'],
  ], [1200, 900, 2450, 1500, 3310]),
  heading('7.2 Profile Gateway protection (profile chính thức)', 2),
  code([
    "# Steady: 10 req/s trong 20 giây",
    "$env:SCENARIO='steady'",
    "$env:RUN_ID=" + '"steady-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"',
    "& 'C:\\Program Files\\k6\\k6.exe' run .\\scripts\\load-test\\submit-load-test.js",
    '',
    '# Spike: 200 VU, mỗi VU submit một lần',
    '$env:RUN_ID="spike-fixture-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"',
    'node .\\scripts\\load-test\\prepare-data.js',
    '$env:SCENARIO="spike"',
    '$env:RUN_ID="spike-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"',
    "& 'C:\\Program Files\\k6\\k6.exe' run .\\scripts\\load-test\\submit-load-test.js",
  ]),
  ...tc('LOAD-01', 'Protected steady 10 req/s', 'Tạo fixture mới, đặt SCENARIO=steady, chạy k6.', 'Có summary JSON mới; p50/p95/p99, 429 và accepted_response được ghi nhận. Gateway/backend không crash.'),
  ...tc('LOAD-02', 'Protected spike 200 VU', 'Tạo fixture mới, đặt SCENARIO=spike, chạy k6.', 'Chỉ có các mã 200/409/429/503/504. Không có xử lý submit/event trùng; Gateway/backend không crash.'),
  heading('7.3 Baseline — chỉ dùng sau khi xác minh', 2),
  callout('Trạng thái hiện tại: CẦN XÁC MINH', 'Không dùng số liệu baseline làm nghiệm thu cho đến khi POST /api/submissions trả JSON 2xx/4xx hợp lệ. Đã từng ghi nhận lỗi HTTP 400 HTML liên quan Host submission_service; nếu còn tái diễn, đánh BLOCKED và đính kèm log.', true),
  code([
    'docker compose -f docker-compose.yml -f gateway/tests/docker-compose.tv4-baseline.yml up -d --force-recreate --no-deps gateway',
    '# kiểm tra POST /api/submissions trả JSON hợp lệ trước khi tạo fixture và chạy k6',
    '# sau khi xong: quay lại Gateway protection',
    'docker compose up -d --force-recreate --no-deps gateway',
  ]),
  ...tc('LOAD-03', 'Baseline steady', 'Chỉ chạy sau pre-check JSON PASS; tạo fixture mới rồi SCENARIO=steady.', 'Kết quả hợp lệ, có summary JSON mới. Nếu POST create trả 400 HTML: BLOCKED, không ghi số liệu.'),
  ...tc('LOAD-04', 'Baseline spike 200 VU', 'Chỉ chạy sau pre-check JSON PASS; tạo fixture mới rồi SCENARIO=spike.', 'Kết quả hợp lệ, có summary JSON mới. Nếu pre-check lỗi: BLOCKED, không ghi số liệu.'),
  heading('8. Bảng tổng hợp số liệu', 1),
  para('Điền từ file scripts/load-test/results/<RUN_ID>-<SCENARIO>-summary.json, log k6 và các truy vấn dữ liệu.'),
  table([
    ['Profile / RUN_ID', 'Requests', '200', '409', '429', '503', '504', 'p50', 'p95', 'p99', 'Tỷ lệ hợp lệ'],
    ['Protected steady\n________________', '______', '____', '____', '____', '____', '____', '____', '____', '____', '__________'],
    ['Protected spike\n________________', '______', '____', '____', '____', '____', '____', '____', '____', '____', '__________'],
    ['Baseline steady\n________________', '______', '____', '____', '____', '____', '____', '____', '____', '____', '__________'],
    ['Baseline spike\n________________', '______', '____', '____', '____', '____', '____', '____', '____', '____', '__________'],
  ], [1500, 650, 550, 550, 550, 550, 550, 600, 600, 600, 1110]),
  table([
    ['Lượt chạy', 'Circuit Breaker', 'Outbox PENDING/SENT sau chạy', 'processed_events / duplicate', 'Crash / lỗi bất thường'],
    ['Protected steady', '________________', '________________________', '________________________', '________________________'],
    ['Protected spike', '________________', '________________________', '________________________', '________________________'],
    ['Baseline (nếu hợp lệ)', '________________', '________________________', '________________________', '________________________'],
  ], [1700, 1800, 2300, 2100, 1460]),
  heading('9. Danh mục bằng chứng cần nộp', 1),
  ...bullets([
    'Ảnh hoặc log docker compose ps, docker compose config --quiet, nginx -t.',
    'Output Maven của Statistics và Submission Service.',
    'Log/ảnh của Gateway harness cho 429, 502, 504 và số request upstream = 1.',
    'Output truy vấn Outbox, processed_events, RabbitMQ cho các demo E2E.',
    'Fixture metadata (RUN_ID, EXAM_ID) và toàn bộ summary.json mới từ k6.',
    'Ảnh biểu đồ k6 hoặc ảnh chụp terminal thể hiện p50/p95/p99, 429 và tỷ lệ hợp lệ.',
  ]),
  table([
    ['Loại bằng chứng', 'Đường dẫn lưu / liên kết (tự điền)'],
    ['Gateway', '________________________________________________________________________________'],
    ['E2E / RabbitMQ / Statistics', '________________________________________________________________________________'],
    ['k6 steady', '________________________________________________________________________________'],
    ['k6 spike', '________________________________________________________________________________'],
    ['Baseline (nếu hợp lệ)', '________________________________________________________________________________'],
  ], [2800, 6560]),
  heading('10. Kết luận và ký xác nhận', 1),
  table([
    ['Mục', 'Nội dung tự điền'],
    ['Kết luận chung', '☐ Đạt  ☐ Đạt có điều kiện  ☐ Chưa đạt\nLý do / rủi ro còn lại: ________________________________________________________________\n__________________________________________________________________________________'],
    ['Các test BLOCKED / N/A', '__________________________________________________________________________________\n__________________________________________________________________________________'],
    ['Người kiểm thử', 'Họ tên: ______________________________  Chữ ký: __________________  Ngày: __________'],
    ['Người rà soát', 'Họ tên: ______________________________  Chữ ký: __________________  Ngày: __________'],
  ], [2400, 6960]),
  para('Ghi chú: Mẫu được thiết kế để điền kết quả thực tế. Không thay thế log gốc, summary.json, hay ảnh chụp bằng chứng.', { size: 18, italics: true, color: DARK, before: 160, after: 0 }),
);

const doc = new Document({
  sections: [{
    properties: { page },
    headers: { default: new Header({ children: [new Paragraph({ children: [run('QUIZ APP · TV4 · MẪU BÁO CÁO KIỂM THỬ', { size: 16, color: DARK, bold: true })], spacing: { after: 0 } })] }) },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, children: [run('TV4 Test Report Template  |  Trang ', { size: 16, color: '667788' }), new TextRun({ children: [PageNumber.CURRENT], size: 16, color: '667788' })], spacing: { before: 0, after: 0 } })] }) },
    children: process.env.LIMIT ? children.slice(0, Number(process.env.LIMIT)) : children,
  }],
  styles: { default: { document: { run: { font: 'Calibri', size: 22, color: INK }, paragraph: { spacing: { after: 120, line: 300 } } } } },
});

await mkdir(dirname(out), { recursive: true });
await writeFile(out, await Packer.toBuffer(doc));
console.log(out);
