export const title = "选中切片的导出快照";
export const state = (v: Record<string, any>) => {
  if (v.phase === "exporting") return "导出中（已固定提交快照）";
  if (!v.selected?.length) return "未选择任何切片";
  if (v.delivered?.length) return "导出完成，可再次选择";
  return "可分享／保存／打包";
};
export const step = (action: string) => {
  if (action.includes("Toggle")) return "用户切换切片选择";
  if (action.includes("StartExport")) return "提交所选切片快照";
  if (action.includes("FinishExport")) return "导出成功";
  if (action.includes("FailExport")) return "导出失败，允许重试";
  return null;
};
