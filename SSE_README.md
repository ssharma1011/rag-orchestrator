# SSE Streaming - Documentation Index

## 🚀 Quick Start

**Having issues with SSE not showing in UI?**

👉 **Start here:** [SSE_INVESTIGATION_SUMMARY.md](SSE_INVESTIGATION_SUMMARY.md)

This contains:
- Analysis of your specific backend + frontend code
- Most likely root cause (React stale closure)
- Quick fix you can apply right now
- What to check in browser console

---

## 📚 Full Documentation

### 1. Investigation Summary ⭐ **READ THIS FIRST**
**File:** [SSE_INVESTIGATION_SUMMARY.md](SSE_INVESTIGATION_SUMMARY.md)

Executive summary with:
- Backend status (working ✅)
- Frontend analysis (event listener correct ✅)
- Most likely issue (stale closure ⚠️)
- Quick fix to apply
- What logs to check

**Start here if:** SSE isn't working and you want to understand why

---

### 2. Targeted Debugging Guide
**File:** [SSE_TARGETED_DEBUG.md](SSE_TARGETED_DEBUG.md)

Step-by-step debugging specific to your codebase:
- Console log checklist
- Network tab verification
- React state debugging
- Data flow tracing
- Ranked list of likely issues

**Use this if:** You want detailed debugging steps

---

### 3. Comprehensive Debugging Reference
**File:** [SSE_DEBUGGING_GUIDE.md](SSE_DEBUGGING_GUIDE.md)

Complete reference covering ALL possible SSE issues:
- Event listener types (onmessage vs addEventListener)
- Field name mismatches
- React state management
- Browser compatibility
- CORS issues
- Connection timing

**Use this if:** You want to understand SSE deeply or the quick fix didn't work

---

### 4. Generic EventListener Fix
**File:** [SSE_FRONTEND_FIX.md](SSE_FRONTEND_FIX.md)

Explains the onmessage vs addEventListener('workflow-update') issue.

**Note:** Your frontend already has this correct, so this file is NOT applicable to your issue. It's kept for reference.

**Use this if:** You're curious about named vs unnamed SSE events

---

### 5. Backend Test Script
**File:** [test-sse.sh](test-sse.sh)

Bash script to test SSE stream directly with curl.

**Usage:**
```bash
# Terminal 1: Start a workflow
curl -X POST http://localhost:8080/api/autoflow/start \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "test-123",
    "requirement": "Explain the codebase",
    "repoUrl": "https://github.com/your/repo"
  }'

# Terminal 2: Watch the stream
./test-sse.sh test-123
```

**Use this if:** You want to verify backend is sending events correctly

---

## 🎯 Decision Tree

**Where should I start?**

```
Are SSE events not showing in the UI?
├─ YES → Read SSE_INVESTIGATION_SUMMARY.md
│         Apply the quick fix
│         Check browser console
│         └─ Still not working?
│            └─ Follow SSE_TARGETED_DEBUG.md steps
│               Report what you find in console/network tab
│
└─ NO, I just want to understand how SSE works
   └─ Read SSE_DEBUGGING_GUIDE.md for complete reference
```

---

## 📊 Backend Status

**Current Status:** ✅ **FULLY WORKING**

Evidence from logs:
```
02:08:07.158  📦 Buffered SSE event (total: 1)
02:08:07.243  📡 Client connected to SSE stream
02:08:07.275  🔄 Replaying 1 buffered events
02:08:25.610  📤 Sent SSE update: agent=__START__
```

Backend is:
- ✅ Buffering events before client connects
- ✅ Replaying buffered events when client connects
- ✅ Sending real-time updates during workflow execution
- ✅ Using correct event name "workflow-update"
- ✅ Serializing JSON correctly

**Conclusion:** The issue is NOT in the backend.

---

## 🔧 Frontend Status

**Current Status:** ⚠️ **Event Listener Correct, State Management Likely Issue**

Your frontend code:
- ✅ Uses `addEventListener('workflow-update')` - CORRECT
- ✅ Has fallback `onmessage` handler - GOOD
- ✅ Has error handling and logging - EXCELLENT
- ⚠️ Callback might have stale closure - LIKELY ISSUE

**Most likely fix:**
```typescript
// Change from:
const handleUpdate = (data) => setUpdates([...updates, data]);

// To:
const handleUpdate = (data) => setUpdates(prev => [...prev, data]);
```

---

## 💡 Quick Diagnostic

**Run this in browser console when workflow is running:**

```javascript
// Check if events are being received
window.addEventListener('message', (e) => console.log('[MESSAGE]', e));

// Or connect directly to verify
const test = new EventSource('http://localhost:8080/api/v1/workflows/YOUR_CONVERSATION_ID/stream');
test.addEventListener('workflow-update', (e) => console.log('[EVENT]', e.data));
test.onerror = (e) => console.error('[ERROR]', e);
```

**If you see events logged:**
- ✅ Backend is working
- ✅ Events are reaching browser
- ❌ Problem is in your React component's state management

**If you don't see events:**
- Check Network tab for the `/stream` connection
- Look for CORS errors
- Verify backend is running on expected port

---

## 📝 Summary Table

| Document | Purpose | When to Use |
|----------|---------|-------------|
| SSE_INVESTIGATION_SUMMARY.md | Quick diagnosis + fix | **START HERE** |
| SSE_TARGETED_DEBUG.md | Step-by-step debugging | Need detailed steps |
| SSE_DEBUGGING_GUIDE.md | Complete reference | Deep dive / complex issues |
| SSE_FRONTEND_FIX.md | Event listener types | Reference only (not your issue) |
| test-sse.sh | Backend verification | Test backend independently |

---

## 🆘 Still Stuck?

If the quick fix doesn't work, please provide:

1. **Browser console logs** - Do you see "📨 SSE Named Event received"?
2. **Network tab screenshot** - Does EventStream tab show events?
3. **Component code** - How are you calling `connectToWorkflowStream`?
4. **After applying fix** - What changed in the logs?

This will help identify the exact issue.
