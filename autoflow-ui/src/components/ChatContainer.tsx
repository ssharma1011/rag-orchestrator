import { useState } from 'react';
import { MessageList } from './MessageList';
import { ChatInput } from './ChatInput';
import { useWorkflowPolling } from '../hooks/useWorkflowPolling';
import { workflowApi } from '../services/api';
import type { ChatMessage, WorkflowStatusResponse } from '../types/workflow';

export function ChatContainer() {
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [currentStatus, setCurrentStatus] = useState<WorkflowStatusResponse | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);

  const handleStatusUpdate = (status: WorkflowStatusResponse) => {
    setCurrentStatus(status);

    // Add or update agent message
    if (status.message) {
      setMessages((prev) => {
        const existingIndex = prev.findIndex(
          (m) => m.role === 'assistant' && m.id === `agent-${status.conversationId}`
        );

        const newMessage: ChatMessage = {
          id: `agent-${status.conversationId}-${Date.now()}`,
          role: 'assistant',
          content: status.message,
          agent: status.currentAgent,
          status: status.status,
          progress: status.progress,
          timestamp: new Date(),
        };

        if (existingIndex >= 0 && status.status === 'RUNNING') {
          // Update existing message if still running
          const updated = [...prev];
          updated[existingIndex] = newMessage;
          return updated;
        } else {
          // Add new message
          return [...prev, newMessage];
        }
      });
    }

    // Stop processing when complete or failed
    if (status.status === 'COMPLETED' || status.status === 'FAILED') {
      setIsProcessing(false);
    }
  };

  useWorkflowPolling({
    conversationId,
    enabled: isProcessing,
    onStatusUpdate: handleStatusUpdate,
  });

  const handleSendMessage = async (message: string) => {
    // Add user message to chat
    const userMessage: ChatMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: message,
      timestamp: new Date(),
    };
    setMessages((prev) => [...prev, userMessage]);
    setIsProcessing(true);

    try {
      if (!conversationId) {
        // Start new workflow
        const status = await workflowApi.startWorkflow({
          requirement: message,
          repoName: 'rag-orchestrator', // TODO: Make this configurable
        });
        setConversationId(status.conversationId);
        handleStatusUpdate(status);
      } else if (currentStatus?.status === 'WAITING_FOR_DEVELOPER') {
        // Respond to workflow questions
        const status = await workflowApi.respond(conversationId, message);
        handleStatusUpdate(status);
      } else {
        // Can't send message while workflow is running
        setMessages((prev) => [
          ...prev,
          {
            id: `error-${Date.now()}`,
            role: 'assistant',
            content: '⚠️ Please wait for the current workflow to complete before sending a new message.',
            timestamp: new Date(),
          },
        ]);
        setIsProcessing(false);
      }
    } catch (error) {
      console.error('Failed to send message:', error);
      setMessages((prev) => [
        ...prev,
        {
          id: `error-${Date.now()}`,
          role: 'assistant',
          content: `❌ Error: ${error instanceof Error ? error.message : 'Failed to send message'}`,
          status: 'FAILED',
          timestamp: new Date(),
        },
      ]);
      setIsProcessing(false);
    }
  };

  const shouldDisableInput =
    isProcessing && currentStatus?.status !== 'WAITING_FOR_DEVELOPER';

  return (
    <div className="flex flex-col h-[calc(100vh-4rem)]">
      <MessageList messages={messages} />
      <ChatInput
        onSendMessage={handleSendMessage}
        disabled={shouldDisableInput}
        placeholder={
          currentStatus?.status === 'WAITING_FOR_DEVELOPER'
            ? 'Answer the questions above...'
            : 'Describe what you want to build or fix...'
        }
      />
    </div>
  );
}
