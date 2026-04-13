import { useEffect, useState, useRef } from 'react';

/**
 * Timer đếm ngược từ deadlineAt.
 * Fix: Dùng expiredRef để đảm bảo onExpire chỉ được gọi đúng 1 lần,
 *      tránh trường hợp gọi submit nhiều lần liên tiếp khi giờ = 0.
 * @param {string} deadlineAt - ISO date string từ server
 * @param {function} onExpire - callback khi hết giờ (chỉ gọi 1 lần)
 */
export default function Timer({ deadlineAt, onExpire }) {
  const [seconds, setSeconds] = useState(null);
  const expiredRef = useRef(false);

  useEffect(() => {
    if (!deadlineAt) return;

    // Reset flags khi deadlineAt thay đổi
    expiredRef.current = false;
    let isFirstTick = true; // Không auto-submit nếu đã hết giờ ngay từ đầu

    const tick = () => {
      // Thêm 'Z' để đảm bảo chuỗi ISO từ server (UTC, không có timezone suffix)
      // được parse đúng là UTC — tránh browser hiểu là giờ địa phương (UTC+7)
      // khiến deadline bị tính là 7 tiếng trước hiện tại → auto-submit ngay lập tức.
      const deadlineUTC = deadlineAt.endsWith('Z') ? deadlineAt : deadlineAt + 'Z';
      const remaining = Math.floor((new Date(deadlineUTC) - Date.now()) / 1000);
      const clamped = Math.max(0, remaining);
      setSeconds(clamped);

      // Bỏ qua nếu hết giờ ngay từ tick đầu tiên
      // (duration=0 hoặc resume sau khi deadline đã qua)
      if (isFirstTick) {
        isFirstTick = false;
        if (clamped === 0) return; // Hiển thị 00:00 nhưng không auto-submit
      }

      // Chỉ gọi onExpire đúng 1 lần khi hết giờ thực sự
      if (clamped === 0 && onExpire && !expiredRef.current) {
        expiredRef.current = true;
        onExpire();
      }
    };

    tick(); // Tính ngay lập tức
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [deadlineAt]); // Không thêm onExpire vào dep để tránh re-create interval

  if (seconds === null) return null;

  const hours = Math.floor(seconds / 3600);
  const mins = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;

  const pad = (n) => String(n).padStart(2, '0');
  const display = hours > 0
    ? `${pad(hours)}:${pad(mins)}:${pad(secs)}`
    : `${pad(mins)}:${pad(secs)}`;

  const timerClass = `timer ${seconds < 60 ? 'danger' : seconds < 300 ? 'warning' : ''}`;

  return (
    <div className={timerClass}>
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="10" />
        <polyline points="12 6 12 12 16 14" />
      </svg>
      <span>{display}</span>
    </div>
  );
}
