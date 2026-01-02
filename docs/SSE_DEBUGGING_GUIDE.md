# SSE Frontend Debugging Guide

## Summary

**Backend Status:** ✅ WORKING CORRECTLY

The logs prove that:
1. ✅ Events are being buffered before client connects
2. ✅ Buffered events are replayed when client connects
3. ✅ New events are sent successfully after connection
4. ✅ JSON serialization is working

**Evidence from logs:**
```
02:08:07.158  📦 Buffered SSE event (total: 1)
02:08:07.243  📡 Client connected to SSE stream
02:08:07.275  🔄 Replaying 1 buffered events  ← REPLAY WORKS!
02:08:25.610  📤 Sent SSE update: agent=__START__
02:08:25.614  📤 Sent SSE update: agent=requirement_analyzer
```

**Conclusion:** The issue is in the frontend event handling or UI rendering.

---

## Backend Event Structure

The backend sends events with this JSON structure:

```json
{
  "conversationId": "0a6f387a-...",
  "status": "RUNNING",
  "agent": "requirement_analyzer",
  "message": "📋 Analyzing requirement...",
  "progress": 0.1,
  "error": null,
  "metadata": null
}
```

**Event Name:** `workflow-update`

**Endpoint:** `GET /api/v1/workflows/{conversationId}/stream`

**Status Values:**
- `RUNNING` - Workflow is executing
- `COMPLETED` - Workflow finished successfully
- `FAILED` - Workflow encountered an error
- `PAUSED` - Workflow paused (waiting for user input)

---

## Potential Frontend Issues

### Issue 1: Event Listener Type Mismatch

**Problem:** Using `onmessage` instead of `addEventListener('workflow-update')`

**Incorrect approach:**
```typescript
const eventSource = new EventSource(url);
eventSource.onmessage = (event) => {  // ❌ Won't receive named events!
  const data = JSON.parse(event.data);
};
```

**Correct approach:**
```typescript
const eventSource = new EventSource(url);
eventSource.addEventListener('workflow-update', (event) => {  // ✅ Correct!
  const data = JSON.parse(event.data);
  console.log('[SSE EVENT]', data);
});
```

**Why:** The backend sends named events using `.name("workflow-update")`, so you MUST use `addEventListener('workflow-update')` instead of the generic `onmessage` handler.

---

### Issue 2: Field Name Mismatch

**Check if frontend expects different field names:**

Backend sends:
```typescript
{
  conversationId: string
  status: "RUNNING" | "COMPLETED" | "FAILED" | "PAUSED"
  agent: string
  message: string
  progress: number  // 0.0 to 1.0
  error?: string
  metadata?: any
}
```

If frontend expects `WorkflowStatusResponse` with fields like:
- `conversation_id` (snake_case) instead of `conversationId`
- `agentName` instead of `agent`
- Different status values like `WAITING_FOR_DEVELOPER` instead of `PAUSED`

**Fix:** Update frontend types to match backend, or add a transformation layer.

---

### Issue 3: React State Not Updating

**Problem:** Event listener updates state but React doesn't re-render

**Common causes:**
1. **Direct mutation of state arrays/objects:**
   ```typescript
   // ❌ Wrong - mutates existing array
   workflowUpdates.push(newUpdate);
   setWorkflowUpdates(workflowUpdates);

   // ✅ Correct - creates new array
   setWorkflowUpdates([...workflowUpdates, newUpdate]);
   ```

2. **EventSource created outside useEffect:**
   ```typescript
   // ❌ Wrong - creates new EventSource on every render
   const eventSource = new EventSource(url);

   // ✅ Correct - manages lifecycle properly
   useEffect(() => {
     const eventSource = new EventSource(url);
     // ... setup listeners
     return () => eventSource.close();
   }, [conversationId]);
   ```

3. **Closure capturing stale state:**
   ```typescript
   // ❌ Wrong - captures initial state value
   eventSource.addEventListener('workflow-update', (event) => {
     setUpdates([...updates, newUpdate]);  // 'updates' is stale!
   });

   // ✅ Correct - uses functional update
   eventSource.addEventListener('workflow-update', (event) => {
     setUpdates(prev => [...prev, newUpdate]);
   });
   ```

---

## Debugging Steps

### Step 1: Verify Events Are Being Received

**Add console.log in the event listener:**

```typescript
const eventSource = new EventSource(`${API_BASE_URL}/workflows/${conversationId}/stream`);

// Try BOTH approaches to see which one works
eventSource.onmessage = (event) => {
  console.log('[SSE onmessage]', event.data);
};

eventSource.addEventListener('workflow-update', (event) => {
  console.log('[SSE workflow-update]', event.data);
});

eventSource.onerror = (error) => {
  console.error('[SSE ERROR]', error);
};
```

**Expected output:**
```
[SSE workflow-update] {"conversationId":"...","status":"RUNNING","agent":"__START__",...}
[SSE workflow-update] {"conversationId":"...","status":"RUNNING","agent":"requirement_analyzer",...}
```

**If you see `[SSE onmessage]` but NOT `[SSE workflow-update]`:**
- The backend is sending default unnamed events instead of named events
- **This is unlikely** since we verified WorkflowStreamService uses `.name("workflow-update")`

**If you see neither:**
- Check browser DevTools Network tab for the `/stream` connection
- Verify EventSource connection is established and not closing prematurely

---

### Step 2: Check Browser Network Tab

**Open DevTools → Network → Filter by "stream"**

You should see:
1. **Request:** `GET /api/v1/workflows/{conversationId}/stream`
2. **Status:** `200 OK`
3. **Type:** `text/event-stream`
4. **Messages:** Click on the request → "EventStream" tab

**Expected messages:**
```
event: workflow-update
data: {"conversationId":"...","status":"RUNNING",...}

event: workflow-update
data: {"conversationId":"...","status":"RUNNING","agent":"requirement_analyzer",...}
```

**If no messages appear:**
- Events are not reaching the browser (unlikely given backend logs)
- Check for CORS errors in Console
- Verify the endpoint URL is correct

---

### Step 3: Verify JSON Parsing

**Add try-catch around JSON.parse:**

```typescript
eventSource.addEventListener('workflow-update', (event) => {
  try {
    const data = JSON.parse(event.data);
    console.log('[PARSED EVENT]', data);

    // Verify all expected fields exist
    console.log('conversationId:', data.conversationId);
    console.log('status:', data.status);
    console.log('agent:', data.agent);
    console.log('message:', data.message);
    console.log('progress:', data.progress);

    onUpdate(data);  // Call your update handler

  } catch (e) {
    console.error('[JSON PARSE ERROR]', event.data, e);
  }
});
```

**If parsing fails:**
- Backend is sending malformed JSON (unlikely)
- Check the exact `event.data` value in the error

---

### Step 4: Verify React State Updates

**Add console.log in your update handler:**

```typescript
const handleWorkflowUpdate = (data: WorkflowEvent) => {
  console.log('[HANDLE UPDATE]', data);

  // Use functional update to avoid stale closure
  setWorkflowUpdates(prev => {
    const updated = [...prev, data];
    console.log('[STATE UPDATED]', updated.length, 'events');
    return updated;
  });
};
```

**If state updates but UI doesn't change:**
- Check if component is re-rendering: add `console.log('[RENDER]')` at component top
- Verify your JSX is reading from the state variable correctly
- Check if there are conditional renders preventing display

---

### Step 5: Check for Type Mismatches

**Verify TypeScript types match backend:**

```typescript
// Backend sends this
interface WorkflowEvent {
  conversationId: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'PAUSED';
  agent?: string;
  message: string;
  progress?: number;  // 0.0 to 1.0
  error?: string;
  metadata?: any;
}

// Does your frontend expect something different?
interface WorkflowStatusResponse {
  conversationId: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'WAITING_FOR_DEVELOPER';  // ⚠️ Different!
  message: string;
  agent?: string;
  progress?: number;
}
```

**Fix:** Either update frontend types to match backend, or add a status mapping:

```typescript
const mapBackendStatus = (backendStatus: string) => {
  if (backendStatus === 'PAUSED') return 'WAITING_FOR_DEVELOPER';
  return backendStatus;
};
```

---

## Quick Test Script

**Create a standalone HTML file to test SSE directly:**

```html
<!DOCTYPE html>
<html>
<head>
  <title>SSE Test</title>
</head>
<body>
  <h1>SSE Test</h1>
  <div id="output"></div>

  <script>
    const conversationId = 'test-12345';  // Replace with real ID
    const eventSource = new EventSource(
      `http://localhost:8080/api/v1/workflows/${conversationId}/stream`
    );

    const output = document.getElementById('output');

    eventSource.addEventListener('workflow-update', (event) => {
      const data = JSON.parse(event.data);
      const div = document.createElement('div');
      div.textContent = `[${data.agent}] ${data.message} (${data.progress * 100}%)`;
      output.appendChild(div);
      console.log('[SSE]', data);
    });

    eventSource.onerror = (error) => {
      console.error('[SSE ERROR]', error);
    };
  </script>
</body>
</html>
```

**If this works but React doesn't:**
- The issue is in React state management or component lifecycle

**If this also doesn't work:**
- CORS issue (check browser console)
- Incorrect endpoint URL
- Backend not running

---

## Expected Behavior

**Timeline:**

1. **T+0ms:** User triggers workflow via `/api/autoflow/start`
2. **T+0-100ms:** Workflow starts, sends first event → buffered (no client yet)
3. **T+200ms:** Frontend connects to `/api/v1/workflows/{id}/stream`
4. **T+200ms:** Backend replays buffered events
5. **T+200ms+:** New events sent in real-time as agents execute
6. **T+end:** Final `COMPLETED` or `FAILED` event closes stream

**What you should see in UI:**
```
[__START__] 🚀 Starting workflow... (0%)
[requirement_analyzer] 📋 Analyzing requirement... (10%)
[code_indexer] 📦 Indexing codebase... (30%)
[documentation_agent] 📚 Generating documentation... (60%)
[__END__] ✅ Workflow completed (100%)
```

---

## Next Steps

1. **Add console.logs** as shown in Step 1
2. **Check Network tab** as shown in Step 2
3. **Report back:**
   - Do you see `[SSE workflow-update]` logs?
   - Do you see events in Network → EventStream tab?
   - Does the standalone HTML test work?

This will help pinpoint whether the issue is:
- ❌ Events not reaching browser (connection issue)
- ❌ Events received but not parsed (event listener issue)
- ❌ Events parsed but state not updating (React issue)
- ❌ State updating but UI not rendering (component issue)
