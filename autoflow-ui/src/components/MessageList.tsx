import { useEffect, useRef } from 'react';
import { UserMessage } from './UserMessage';
import { AgentMessage } from './AgentMessage';
import type { ChatMessage } from '../types/workflow';

interface MessageListProps {
  messages: ChatMessage[];
}

export function MessageList({ messages }: MessageListProps) {
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  if (messages.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="text-center max-w-md px-4">
          <h2 className="text-2xl font-semibold text-gray-700 dark:text-gray-300 mb-2">
            Welcome to AutoFlow
          </h2>
          <p className="text-gray-500 dark:text-gray-400">
            Start a conversation by describing what you'd like to build or fix.
            AutoFlow will analyze your codebase and help you implement your requirements.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-y-auto px-4 py-6 scrollbar-thin">
      <div className="max-w-4xl mx-auto">
        {messages.map((message) =>
          message.role === 'user' ? (
            <UserMessage key={message.id} message={message} />
          ) : (
            <AgentMessage key={message.id} message={message} />
          )
        )}
        <div ref={messagesEndRef} />
      </div>
    </div>
  );
}
