export const title = "打开图片与分享交付";
export const state = (v: Record<string, any>) => {
  if (v.inFlight?.length) return v.displayed ? "新图加载中（旧图仍可见）" : "正在读取原图";
  return v.displayed ? "当前图片准备完成" : "尚未显示图片";
};
export const step = (action: string) => {
  if (action.includes("OpenPicker")) return "用户选择相册图片";
  if (action.includes("OpenShare")) return "其他应用分享图片";
  if (action.includes("Complete")) return "图片分析任务结束（旧结果被丢弃）";
  if (action.includes("Ack")) return "界面确认接收分享";
  return null;
};
