# SSE Frontend Fix - Most Likely Root Cause

## TL;DR

**Backend is working perfectly.** The issue is 99% likely to be this:

**Frontend is using `eventSource.onmessage` instead of `eventSource.addEventListener('workflow-update')`**

---

## The Problem

SSE supports two types of events:

### 1. Default unnamed events
```typescript
// Backend sends like this:
emitter.send(data);  // No event name

// Frontend receives like this:
eventSource.onmessage = (event) => { ... };  // ✅ Works
```

### 2. Named events (what we're using)
```typescript
// Backend sends like this:
emitter.send(SseEmitter.event()
  .name("workflow-update")  // ← Named event!
  .data(json));

// Frontend receives like this:
eventSource.addEventListener('workflow-update', (event) => { ... });  // ✅ Works
eventSource.onmessage = (event) => { ... };  // ❌ DOESN'T WORK!
```

**Our backend uses named events (`workflow-update`), so the frontend MUST use `addEventListener('workflow-update')`.**

---

## The Fix

### ❌ Current Code (Broken)

```typescript
// workflowService.ts
export const connectToWorkflowStream = (
  conversationId: string,
  onUpdate: (data: WorkflowStatusResponse) => void,
  onError: (error: string) => void
) => {
  const eventSource = new EventSource(
    `${API_BASE_URL}/workflows/${conversationId}/stream`
  );

  // ❌ This won't receive named events!
  eventSource.onmessage = (event) => {
    const data = JSON.parse(event.data);
    onUpdate(data);
  };

  eventSource.onerror = (error) => {
    onError('Stream connection failed');
    eventSource.close();
  };

  return eventSource;
};
```

### ✅ Fixed Code

```typescript
// workflowService.ts
export const connectToWorkflowStream = (
  conversationId: string,
  onUpdate: (data: WorkflowStatusResponse) => void,
  onError: (error: string) => void
) => {
  const eventSource = new EventSource(
    `${API_BASE_URL}/workflows/${conversationId}/stream`
  );

  // ✅ Listen to the specific named event
  eventSource.addEventListener('workflow-update', (event) => {
    try {
      const data = JSON.parse(event.data);
      console.log('[SSE Update]', data);
      onUpdate(data);
    } catch (e) {
      console.error('[SSE Parse Error]', event.data, e);
    }
  });

  eventSource.onerror = (error) => {
    console.error('[SSE Connection Error]', error);
    onError('Stream connection failed');
    eventSource.close();
  };

  return eventSource;
};
```

**Key change:** Replace `eventSource.onmessage` with `eventSource.addEventListener('workflow-update', ...)`

---

## Type Compatibility

The backend `WorkflowEvent` should be compatible with frontend `WorkflowStatusResponse`.

### Backend (WorkflowEvent)
```typescript
{
  conversationId: string;
  status: "RUNNING" | "COMPLETED" | "FAILED" | "PAUSED";
  agent?: string;
  message: string;
  progress?: number;  // 0.0 to 1.0
  error?: string;
  metadata?: any;
}
```

### Frontend (WorkflowStatusResponse) - Assumed
```typescript
{
  conversationId: string;
  status: "RUNNING" | "COMPLETED" | "FAILED" | "WAITING_FOR_DEVELOPER";
  message: string;
  agent?: string;
  progress?: number;
}
```

**Minor mismatch:** Backend uses `PAUSED`, frontend expects `WAITING_FOR_DEVELOPER`.

**Fix if needed:**
```typescript
eventSource.addEventListener('workflow-update', (event) => {
  const data = JSON.parse(event.data);

  // Map PAUSED to WAITING_FOR_DEVELOPER if needed
  if (data.status === 'PAUSED') {
    data.status = 'WAITING_FOR_DEVELOPER';
  }

  onUpdate(data);
});
```

---

## React Component Fix (if needed)

If you're managing the EventSource in a React component:

### ✅ Correct Pattern

```typescript
// App.tsx or WorkflowComponent.tsx
const [updates, setUpdates] = useState<WorkflowEvent[]>([]);

useEffect(() => {
  if (!conversationId) return;

  const eventSource = connectToWorkflowStream(
    conversationId,
    // ⚠️ Use functional update to avoid stale closure!
    (data) => setUpdates(prev => [...prev, data]),
    (error) => console.error(error)
  );

  // Cleanup on unmount
  return () => {
    eventSource.close();
  };
}, [conversationId]);  // Re-create if conversationId changes
```

---

## Verification Steps

### 1. Test with curl (Backend verification)

```bash
# Start a workflow first to generate events
curl -X POST http://localhost:8080/api/autoflow/start \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "test-123",
    "requirement": "Explain the codebase",
    "repoUrl": "https://github.com/spring-projects/spring-petclinic"
  }'

# In another terminal, listen to the stream
./test-sse.sh test-123
```

**Expected output:**
```
event: workflow-update
data: {"conversationId":"test-123","status":"RUNNING","agent":"__START__",...}

event: workflow-update
data: {"conversationId":"test-123","status":"RUNNING","agent":"requirement_analyzer",...}
```

**If you see this, backend is working! ✅**

### 2. Test with browser console

Open your frontend app, then in DevTools Console:

```javascript
// Quick test
const eventSource = new EventSource(
  'http://localhost:8080/api/v1/workflows/test-123/stream'
);

// Try BOTH to see which one receives events
eventSource.onmessage = (e) => console.log('[onmessage]', e.data);
eventSource.addEventListener('workflow-update', (e) => console.log('[workflow-update]', e.data));
```

**If you see `[workflow-update]` but NOT `[onmessage]`:**
- Confirms the backend is sending named events
- Confirms you need to use `addEventListener('workflow-update')`

### 3. Check Network Tab

DevTools → Network → Filter "stream" → Click the request → "EventStream" tab

You should see:
```
event: workflow-update
data: {"conversationId":"...","status":"RUNNING",...}
```

If the **event:** line says `workflow-update`, you MUST use `addEventListener('workflow-update')`.

---

## Summary

**What's working:**
- ✅ Backend SSE endpoint
- ✅ Event buffering and replay
- ✅ JSON serialization
- ✅ Event sending

**What's broken:**
- ❌ Frontend is likely using `onmessage` instead of `addEventListener('workflow-update')`

**The fix:**
```typescript
// Change this:
eventSource.onmessage = (event) => { ... };

// To this:
eventSource.addEventListener('workflow-update', (event) => { ... });
```

That's it! This single change should fix the issue.
