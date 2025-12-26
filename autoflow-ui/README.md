# AutoFlow UI

Modern React-based user interface for the AutoFlow AI Code Assistant.

## Features

- **Real-time Chat Interface**: Chat with AI agents to build features, fix bugs, and understand codebases
- **Status Polling**: Automatic polling of workflow status every 2 seconds
- **Agent Progress Tracking**: Visual progress indicators and status updates
- **Markdown Rendering**: Full markdown support with syntax highlighting for code blocks
- **Metrics Dashboard**: Monitor agent performance, token usage, costs, and latency
- **Dark Mode Support**: Built-in dark mode styling (via Tailwind)

## Tech Stack

- **React 18** - UI framework
- **TypeScript** - Type safety
- **Tailwind CSS** - Styling
- **Vite** - Build tool
- **React Router** - Client-side routing
- **Axios** - HTTP client
- **React Markdown** - Markdown rendering
- **Recharts** - Data visualization
- **Lucide React** - Icons

## Getting Started

### Prerequisites

- Node.js 18+ and npm
- AutoFlow backend running on `http://localhost:8080`

### Installation

```bash
# Install dependencies
npm install
```

### Development

```bash
# Start development server (http://localhost:3000)
npm run dev
```

The dev server includes proxy configuration to forward `/api/*` requests to the backend at `http://localhost:8080`.

### Build

```bash
# Type check
npm run lint

# Build for production
npm run build

# Preview production build
npm run preview
```

## Project Structure

```
src/
├── components/          # React components
│   ├── ChatContainer.tsx       # Main chat orchestrator
│   ├── MessageList.tsx         # Message list renderer
│   ├── UserMessage.tsx         # User message bubble
│   ├── AgentMessage.tsx        # Agent message with progress
│   ├── ChatInput.tsx           # Message input with auto-resize
│   ├── Header.tsx              # App header with navigation
│   └── MetricsDashboard.tsx    # Metrics visualization
├── services/            # API services
│   ├── api.ts                  # Workflow API client
│   └── metrics.ts              # Metrics API client
├── types/               # TypeScript definitions
│   ├── workflow.ts             # Workflow types
│   └── metrics.ts              # Metrics types
├── hooks/               # Custom React hooks
│   └── useWorkflowPolling.ts   # Polling hook
├── App.tsx              # Main app with routing
├── main.tsx             # Entry point
└── index.css            # Global styles
```

## API Integration

The UI connects to the AutoFlow backend API:

- **POST** `/api/v1/workflows/start` - Start new workflow
- **GET** `/api/v1/workflows/{id}/status` - Poll workflow status
- **POST** `/api/v1/workflows/{id}/respond` - Respond to workflow questions
- **GET** `/api/v1/metrics/system` - Get system-wide metrics
- **GET** `/api/v1/metrics/conversation/{id}` - Get conversation metrics
- **GET** `/api/v1/metrics/agent/{name}` - Get agent-specific metrics
- **POST** `/api/v1/metrics/reset` - Reset all metrics

## Usage

1. **Start a Conversation**: Describe what you want to build or fix
2. **Monitor Progress**: Watch as agents analyze your codebase and work on the task
3. **Answer Questions**: If agents need clarification, they'll ask - answer directly in the chat
4. **View Metrics**: Click "Metrics" to see performance data and resource usage

## Phase 1 MVP (Current Implementation)

✅ Core chat interface with async workflow support
✅ Status polling mechanism (2-second intervals)
✅ Message components with progress indicators
✅ Markdown rendering with code highlighting
✅ Metrics dashboard with charts and stats
✅ Responsive design with Tailwind CSS

## Future Enhancements (Phase 2 & 3)

- [ ] WebSocket support for real-time updates
- [ ] File upload functionality
- [ ] Dark mode toggle
- [ ] Conversation history persistence
- [ ] Export conversations
- [ ] Multi-repository selector
- [ ] Settings panel
