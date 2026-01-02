# SSE Targeted Debugging - Frontend Analysis

## Good News

Your frontend code in `workflowService.ts` **already has the correct implementation**:

```typescript
// ✅ Line ~78 - This is CORRECT
eventSource.addEventListener('workflow-update', (event) => {
  console.log('📨 SSE Named Event received:', event.data);
  const data: WorkflowStatusResponse = JSON.parse(event.data);
  onUpdate(data);
  ...
});
```

So the event listener is properly configured. Let's figure out why events aren't showing in the UI.

---

## Debugging Checklist

### Step 1: Check Browser Console Logs

Open your frontend app and start a workflow. Look for these console messages:

**Expected logs:**
```
📡 Connecting to SSE: http://localhost:8080/api/v1/workflows/{id}/stream
✅ SSE Connection Opened: http://localhost:8080/api/v1/workflows/{id}/stream
📨 SSE Named Event received: {"conversationId":"...","status":"RUNNING",...}
📨 SSE Named Event received: {"conversationId":"...","status":"RUNNING","agent":"requirement_analyzer",...}
```

**What to look for:**

1. ✅ **If you see "SSE Named Event received":**
   - Events ARE reaching the frontend
   - The problem is in the `onUpdate` handler or React state management
   - **Jump to Step 3**

2. ❌ **If you see "SSE Connection Opened" but NO "Named Event received":**
   - Connection established but events not received
   - Check Network tab (Step 2)

3. ❌ **If you see neither:**
   - Connection isn't being established
   - Check for CORS errors in console
   - Verify backend is running on the expected port

---

### Step 2: Check Network Tab

**DevTools → Network → Filter "stream"**

1. Find the request: `GET /api/v1/workflows/{id}/stream`
2. **Check Status:**
   - ✅ Should be `200 OK` with Type `text/event-stream`
   - ❌ If `404 Not Found`: Backend endpoint not available
   - ❌ If `CORS error`: Backend CORS configuration issue (unlikely since we have `@CrossOrigin`)

3. **Check EventStream tab:**
   - Click on the `/stream` request
   - Look for "EventStream" or "Messages" tab
   - You should see:
     ```
     event: workflow-update
     data: {"conversationId":"...","status":"RUNNING",...}

     event: workflow-update
     data: {"conversationId":"...","status":"RUNNING","agent":"requirement_analyzer",...}
     ```

4. **If you see events in Network tab but NOT in console:**
   - Events are arriving but the event listener isn't firing
   - This would be VERY strange since the code looks correct
   - Possible browser issue or EventSource polyfill problem

---

### Step 3: Debug React State Updates

If events ARE being received (you see console logs), but UI doesn't update, the issue is in your React component.

**Add debugging to your App.tsx or component:**

```typescript
const [workflowUpdates, setWorkflowUpdates] = useState<WorkflowStatusResponse[]>([]);

// Add this console.log at the TOP of your component render
console.log('[RENDER] Current updates:', workflowUpdates.length);

const handleWorkflowUpdate = (data: WorkflowStatusResponse) => {
  console.log('[HANDLE UPDATE] Received:', data);

  // ⚠️ CRITICAL: Use functional update to avoid stale closure
  setWorkflowUpdates(prev => {
    const updated = [...prev, data];
    console.log('[STATE UPDATED] Total events:', updated.length);
    return updated;
  });
};

useEffect(() => {
  if (!conversationId) return;

  console.log('[EFFECT] Creating EventSource for:', conversationId);

  const eventSource = workflowService.connectToWorkflowStream(
    conversationId,
    handleWorkflowUpdate,  // ⚠️ This might capture stale state!
    (error) => console.error('[SSE ERROR]', error)
  );

  return () => {
    console.log('[CLEANUP] Closing EventSource');
    eventSource.close();
  };
}, [conversationId]);  // ⚠️ Should handleWorkflowUpdate be in deps?
```

**Look for these issues:**

1. **Stale Closure Problem:**
   ```typescript
   // ❌ BAD - handleWorkflowUpdate not in deps, captures stale state
   useEffect(() => {
     const eventSource = workflowService.connectToWorkflowStream(
       conversationId,
       handleWorkflowUpdate,  // This function might be using old state!
       ...
     );
   }, [conversationId]);  // Missing handleWorkflowUpdate in deps
   ```

   **Fix:**
   ```typescript
   // ✅ GOOD - Use inline callback with functional update
   useEffect(() => {
     const eventSource = workflowService.connectToWorkflowStream(
       conversationId,
       (data) => setWorkflowUpdates(prev => [...prev, data]),  // Inline, always fresh
       ...
     );
   }, [conversationId]);
   ```

2. **Direct State Mutation:**
   ```typescript
   // ❌ BAD
   workflowUpdates.push(newUpdate);
   setWorkflowUpdates(workflowUpdates);  // React won't detect change!

   // ✅ GOOD
   setWorkflowUpdates([...workflowUpdates, newUpdate]);
   // or better:
   setWorkflowUpdates(prev => [...prev, newUpdate]);
   ```

3. **Component Not Re-rendering:**
   - Check if `console.log('[RENDER]')` appears when events arrive
   - If state updates but no re-render, check:
     - Are you using `React.memo()` without proper deps?
     - Is the component unmounting/remounting unexpectedly?

---

### Step 4: Verify Data Flow

Add logging through the entire chain:

```typescript
// workflowService.ts
eventSource.addEventListener('workflow-update', (event) => {
  console.log('[1. SSE RECEIVED]', event.data);
  try {
    const data: WorkflowStatusResponse = JSON.parse(event.data);
    console.log('[2. PARSED]', data);
    onUpdate(data);
    console.log('[3. CALLBACK CALLED]');
  } catch (e) {
    console.error('[ERROR PARSING]', e);
  }
});

// App.tsx
const handleWorkflowUpdate = (data: WorkflowStatusResponse) => {
  console.log('[4. HANDLER RECEIVED]', data);
  setWorkflowUpdates(prev => {
    console.log('[5. PREV STATE]', prev.length);
    const updated = [...prev, data];
    console.log('[6. NEW STATE]', updated.length);
    return updated;
  });
};

// In your JSX
console.log('[7. RENDERING]', workflowUpdates.length, 'updates');
```

**Trace the flow:**
1. Event received → [1. SSE RECEIVED]
2. Parsed successfully → [2. PARSED]
3. Callback invoked → [3. CALLBACK CALLED]
4. Handler called → [4. HANDLER RECEIVED]
5. State updater runs → [5. PREV STATE] → [6. NEW STATE]
6. Component renders → [7. RENDERING]

**Where does the chain break?**
- Breaks at step 1-3: Event listener issue
- Breaks at step 4: Callback not wired correctly
- Breaks at step 5-6: State update issue
- Breaks at step 7: Render issue

---

## Most Likely Issues (Ranked)

### 1. **Stale Closure in useEffect** (80% probability)

Your `handleWorkflowUpdate` function is defined outside useEffect and uses state. When passed to `connectToWorkflowStream`, it captures the state at that moment.

**Fix:**
```typescript
useEffect(() => {
  if (!conversationId) return;

  const eventSource = workflowService.connectToWorkflowStream(
    conversationId,
    // Use inline function with functional update
    (data) => {
      console.log('[UPDATE]', data);
      setWorkflowUpdates(prev => [...prev, data]);
    },
    (error) => console.error(error)
  );

  return () => eventSource.close();
}, [conversationId]);
```

### 2. **EventSource Connection Timing** (10% probability)

You might be creating the EventSource before the backend workflow starts, but this SHOULD be handled by the buffering we implemented.

**Verify timing:**
```typescript
// Is this happening in the right order?
// 1. User clicks "Start Workflow"
// 2. Call startWorkflow() → gets conversationId
// 3. Call connectToWorkflowStream(conversationId)

// Make sure you're not connecting BEFORE getting conversationId!
```

### 3. **Component Unmounting/Remounting** (5% probability)

React StrictMode or routing might be causing the component to unmount and remount, closing the EventSource.

**Check:**
- Is your app wrapped in `<React.StrictMode>`? (causes double-mount in dev)
- Are you navigating away and back?

### 4. **Browser EventSource Issues** (5% probability)

Some browsers have quirks with EventSource.

**Test in different browser:**
- Try Chrome, Firefox, Safari
- Check if any browser extensions are blocking SSE

---

## Quick Fix to Try First

Replace your EventSource connection with this:

```typescript
// App.tsx or WorkflowPage.tsx
const [updates, setUpdates] = useState<WorkflowStatusResponse[]>([]);
const [conversationId, setConversationId] = useState<string | null>(null);
const eventSourceRef = useRef<EventSource | null>(null);

useEffect(() => {
  if (!conversationId) return;

  console.log('🔌 Connecting to SSE for:', conversationId);

  const eventSource = new EventSource(
    `http://localhost:8080/api/v1/workflows/${conversationId}/stream`
  );

  eventSource.addEventListener('workflow-update', (event) => {
    console.log('📨 Event:', event.data);
    const data = JSON.parse(event.data);

    // Direct state update with functional form
    setUpdates(prev => {
      const newUpdates = [...prev, data];
      console.log('📊 Total updates:', newUpdates.length);
      return newUpdates;
    });
  });

  eventSource.onerror = (error) => {
    console.error('❌ SSE Error:', error);
  };

  eventSourceRef.current = eventSource;

  return () => {
    console.log('🔌 Closing SSE');
    eventSource.close();
  };
}, [conversationId]);  // Only conversationId in deps
```

---

## What to Report Back

Please provide:

1. **Console logs** - Do you see "📨 SSE Named Event received"?
2. **Network tab** - Do you see events in the EventStream tab?
3. **Component rendering** - Does `console.log('[RENDER]')` show increasing update count?
4. **Browser** - Which browser are you using?
5. **Any errors** - Any red errors in console?

This will help pinpoint exactly where the chain breaks.
