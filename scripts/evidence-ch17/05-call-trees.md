# 第 17 掌：按 traceId 还原出来的调用树（真实运行）

## success
traceId=bc31412237e6a51bb6aa1e20e9923365  spans=3  failedSpans=0  failureTypes=[]
- [http] http.request  outcome=ok  failure=-  duration=2310ms
  - [model] genai.chat  outcome=ok  failure=-  duration=2271ms
    - [tool] genai.tool  outcome=ok  failure=-  duration=31ms

## failure
traceId=52811492399507fd110039713e1650e6  spans=1  failedSpans=1  failureTypes=[INPUT_INVALID]
- [http] http.request  outcome=rejected  failure=INPUT_INVALID  duration=24ms

## rag
traceId=10314136b692f48b7111fbb956bea695  spans=2  failedSpans=0  failureTypes=[]
- [http] http.request  outcome=ok  failure=-  duration=1092ms
  - [rag] genai.rag.answer  outcome=ok  failure=-  duration=1082ms

