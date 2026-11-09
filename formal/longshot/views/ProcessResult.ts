export const title = "切片处理完成与一次性导航";
export const state = (v: Record<string, any>) => {
  if (v.phase === "processing") return "正在生成切片";
  if (v.pending) return "结果待展示";
  if (v.phase === "failed") return "处理失败，可重试";
  return v.sequence ? "结果已消费或可继续编辑" : "裁切设置";
};
export const step = (action: string) => {
  if (action.includes("Begin")) return "用户点击生成";
  if (action.includes("Complete")) return "成功生成切片";
  if (action.includes("Fail")) return "切片任务失败";
  if (action.includes("Ack")) return "结果页面确认导航";
  if (action.includes("ManualEdit")) return "手动调整切片";
  return null;
};
