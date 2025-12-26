export interface WorkflowRequest {
  requirement: string;
  repoName: string;
  targetClass?: string;
}

export type WorkflowStatus = 'RUNNING' | 'WAITING_FOR_DEVELOPER' | 'COMPLETED' | 'FAILED';

export interface WorkflowStatusResponse {
  conversationId: string;
  status: WorkflowStatus;
  message: string;
  currentAgent?: string;
  progress?: number;
  error?: string;
  timestamp: string;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  agent?: string;
  status?: WorkflowStatus;
  progress?: number;
  timestamp: Date;
}

export interface RespondRequest {
  response: string;
}
