// ============ 提醒中心：所有提醒设置集中在这里 ============
// 对应仓库：ReminderScheduler（晨/晚测量）、MedicationReminders（服药）、
// MedicationCalendarSync（可选写入系统日历，需授权）、通知权限与闹钟分离。
// 原型阶段：「保存」只写入本地状态（占位），不真正调度系统闹钟。
const REMINDERS_INIT = {
  amOn: true, amTime: '07:30',
  pmOn: true, pmTime: '21:00',
  days: [1, 2, 3, 4, 5, 6, 0],
  snooze: true,
  medOn: true,
  medTimes: {},          // { [medId]: { on, time } }，缺省取药品自身时间
  calendar: false,
  backupOn: true, backupDays: 30,
};
const DAY_ORDER = [1, 2, 3, 4, 5, 6, 0];

function remindersSummary(r, meds) {
  const m = (r.amOn ? 1 : 0) + (r.pmOn ? 1 : 0);
  const d = r.medOn ? meds.filter((x) => (r.medTimes[x.id] ? r.medTimes[x.id].on : true)).length : 0;
  return `测量 ${m} 个 · 服药 ${d} 个已开启`;
}

function ReminderCenter({ value, meds, onSave, onClose, toast }) {
  const [r, setR] = useState(() => JSON.parse(JSON.stringify(value)));
  const [out, setOut] = useState(false);
  const [ask, setAsk] = useState(false);
  const [notifGranted, setNotifGranted] = useState(false);
  const [calGranted, setCalGranted] = useState(false);
  const dirty = JSON.stringify(r) !== JSON.stringify(value);
  const set = (k, v) => setR((x) => ({ ...x, [k]: v }));
  const medCfg = (m) => r.medTimes[m.id] || { on: true, time: m.time };
  const setMed = (m, patch) => setR((x) => ({ ...x, medTimes: { ...x.medTimes, [m.id]: { ...medCfg(m), ...patch } } }));
  const toggleDay = (d) => set('days', r.days.includes(d) ? r.days.filter((x) => x !== d) : [...r.days, d]);

  const leave = () => { setOut(true); setTimeout(onClose, 250); };
  const back = () => (dirty ? setAsk(true) : leave());
  const save = () => { onSave(r); toast('提醒设置已保存（原型占位，未调度系统闹钟）'); leave(); };
  const noDays = (r.amOn || r.pmOn) && r.days.length === 0;

  const TimeRow = ({ on, time, onTime, note }) => on ? (
    <div className="rm-time">
      <input type="time" className="num" value={time} onChange={(e) => onTime(e.target.value)} aria-label="提醒时间" />
      {note && <span className="faint">{note}</span>}
    </div>
  ) : null;

  return (
    <div className={'sub' + (out ? ' out' : '')} data-screen-label="08 提醒中心">
      <div className="flow-head">
        <button className="icon-btn" onClick={back} aria-label="返回"><Icon n="back" /></button>
        <div className="ttl">提醒中心</div>
      </div>
      <div className="sub-body">
        {!notifGranted && (
          <div className="perm" role="status">
            <Icon n="bell" />
            <div>
              <b>通知权限未开启。</b>提醒仍会按时触发，但不会弹出通知。
              <div><button onClick={() => { setNotifGranted(true); toast('已开启通知（模拟）'); }}>去开启</button></div>
            </div>
          </div>
        )}

        <div className="sec-head"><h2 className="h2">测量提醒</h2><span className="more">早晚各一次</span></div>
        <div className="group">
          <SwitchRow ic="sun" h={75} title="早上测量" sub="起床后、服药和早饭前" on={r.amOn} onChange={(v) => set('amOn', v)} />
          <TimeRow on={r.amOn} time={r.amTime} onTime={(v) => set('amTime', v)} />
          <SwitchRow ic="moon" h={270} title="晚上测量" sub="睡前测一次" on={r.pmOn} onChange={(v) => set('pmOn', v)} />
          <TimeRow on={r.pmOn} time={r.pmTime} onTime={(v) => set('pmTime', v)} />
          <div className={'row' + (r.amOn || r.pmOn ? '' : ' dim')} style={{ display: 'block' }}>
            <div style={{ fontWeight: 700, fontSize: '.92rem', marginBottom: 10 }}>重复</div>
            <div className="chips" role="group" aria-label="重复日期">
              {DAY_ORDER.map((d) => <button key={d} className={'chip' + (r.days.includes(d) ? ' on' : '')} aria-pressed={r.days.includes(d)} style={{ width: '2.6rem', padding: 0, justifyContent: 'center' }} onClick={() => toggleDay(d)}>{WD[d]}</button>)}
            </div>
            {noDays && <div className="field-err">至少选一天，否则测量提醒不会响</div>}
          </div>
          <SwitchRow ic="clock" h={200} title="没测就再提醒一次" sub="30 分钟后未记录时再响一次" on={r.snooze} onChange={(v) => set('snooze', v)} />
        </div>

        <div className="sec-head"><h2 className="h2">服药提醒</h2><span className="more">{meds.length} 种药</span></div>
        <div className="group">
          <SwitchRow ic="pill" h={20} title="服药提醒" sub="按每种药的时间提醒，首页可打卡" on={r.medOn} onChange={(v) => set('medOn', v)} />
          {meds.map((m) => {
            const c = medCfg(m);
            return (
              <React.Fragment key={m.id}>
                <SwitchRow ic="pill" h={m.mh} title={m.name} sub={m.dose} on={r.medOn && c.on} dim={!r.medOn} onChange={(v) => setMed(m, { on: v })} />
                <TimeRow on={r.medOn && c.on} time={c.time} onTime={(v) => setMed(m, { time: v })} note="改时间不会删除过去的打卡" />
              </React.Fragment>
            );
          })}
          <NavRow ic="plus" h={155} title="添加或管理药品" sub="药名、剂量、每日多个时间点" onClick={() => toast('药品管理 · 原型中未展开')} />
        </div>
        <div className="hint">修改时间只影响之后的提醒；历史打卡按原计划时间保留。</div>

        <div className="sec-head"><h2 className="h2">系统日历</h2><span className="more">可选</span></div>
        <div className="group">
          <SwitchRow ic="cal" h={250} title="服药提醒写入系统日历" sub={calGranted ? '已授权 · 在日历 App 里也能看到' : '开启时会申请日历权限'} on={r.calendar}
            onChange={(v) => { if (v && !calGranted) { setCalGranted(true); toast('已授予日历权限（模拟）'); } set('calendar', v); }} />
        </div>
        <div className="hint">不开启就不会申请日历权限；关闭后会清理已写入的日程。关闭服药提醒不受日历权限影响。</div>

        <div className="sec-head"><h2 className="h2">备份提醒</h2></div>
        <div className="group">
          <SwitchRow ic="export" h={155} title="定期提醒导出备份" sub="数据只在本机，卸载或换机前需要备份" on={r.backupOn} onChange={(v) => set('backupOn', v)} />
          {r.backupOn && (
            <div className="row" style={{ display: 'block' }}>
              <div className="seg" role="radiogroup" aria-label="备份提醒间隔">
                {[7, 30, 90].map((n) => <button key={n} role="radio" aria-checked={r.backupDays === n} className={r.backupDays === n ? 'on' : ''} onClick={() => set('backupDays', n)}>每 {n} 天</button>)}
              </div>
              <div className="faint" style={{ fontSize: '.78rem', marginTop: 8 }}>上次备份：还没有备份过</div>
            </div>
          )}
        </div>
      </div>
      <div className="sub-foot">
        <button className="btn btn-primary btn-block" disabled={!dirty || noDays} onClick={save}>{dirty ? '保存提醒设置' : '没有改动'}</button>
      </div>
      {ask && <Confirm title="还没保存，要离开吗？" body="刚才的提醒改动不会生效。" ok="不保存，离开" cancel="继续编辑" danger onOk={() => { setAsk(false); leave(); }} onCancel={() => setAsk(false)} />}
    </div>
  );
}
Object.assign(window, { ReminderCenter, REMINDERS_INIT, remindersSummary });
