import { User } from 'lucide-react';
import type { ChatMessage } from '../types/workflow';

interface UserMessageProps {
  message: ChatMessage;
}

export function UserMessage({ message }: UserMessageProps) {
  return (
    <div className="flex justify-end items-start space-x-3 mb-6">
      <div className="flex-1 max-w-3xl">
        <div className="bg-blue-600 text-white rounded-2xl rounded-tr-sm px-4 py-3 shadow-sm">
          <div className="whitespace-pre-wrap break-words">{message.content}</div>
        </div>
        <div className="text-xs text-gray-500 dark:text-gray-400 mt-1 text-right">
          {message.timestamp.toLocaleTimeString()}
        </div>
      </div>
      <div className="flex-shrink-0 w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center">
        <User size={18} className="text-white" />
      </div>
    </div>
  );
}
