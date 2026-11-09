export const title = "切线拖动与按实际像素裁切";
export const state = (v: Record<string, any>) => {
  if (v.phase === "encoding") return "正在重切片";
  if (v.phase === "editing") return "切线已调整（未写盘）";
  return "裁切结果已准备";
};
export const step = (action: string) => {
  if (action.includes("Drag")) return "用户移动切线";
  if (action.includes("CommitChanged")) return "边界变化，提交重切片";
  if (action.includes("CommitNoop")) return "边界不变，跳过编码";
  if (action.includes("FinishEncode")) return "切片写入完成";
  return null;
};
