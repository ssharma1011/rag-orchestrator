import { Bot, Loader2, CheckCircle, AlertCircle } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import type { ChatMessage } from '../types/workflow';

interface AgentMessageProps {
  message: ChatMessage;
}

export function AgentMessage({ message }: AgentMessageProps) {
  const isRunning = message.status === 'RUNNING';
  const isCompleted = message.status === 'COMPLETED';
  const isFailed = message.status === 'FAILED';

  const getStatusIcon = () => {
    if (isRunning) return <Loader2 size={16} className="animate-spin text-blue-500" />;
    if (isCompleted) return <CheckCircle size={16} className="text-green-500" />;
    if (isFailed) return <AlertCircle size={16} className="text-red-500" />;
    return null;
  };

  const getAgentColor = () => {
    if (isFailed) return 'bg-red-500';
    if (isRunning) return 'bg-blue-500';
    return 'bg-green-500';
  };

  return (
    <div className="flex items-start space-x-3 mb-6">
      <div className={`flex-shrink-0 w-8 h-8 rounded-full ${getAgentColor()} flex items-center justify-center`}>
        <Bot size={18} className="text-white" />
      </div>
      <div className="flex-1 max-w-3xl">
        {message.agent && (
          <div className="flex items-center space-x-2 mb-2">
            <span className="text-xs font-medium text-gray-700 dark:text-gray-300">{message.agent}</span>
            {getStatusIcon()}
          </div>
        )}
        <div className="bg-gray-100 dark:bg-gray-700 rounded-2xl rounded-tl-sm px-4 py-3 shadow-sm">
          <div className="prose prose-sm dark:prose-invert max-w-none">
            <ReactMarkdown
              components={{
                code({ node, inline, className, children, ...props }) {
                  const match = /language-(\w+)/.exec(className || '');
                  return !inline && match ? (
                    <SyntaxHighlighter
                      style={vscDarkPlus}
                      language={match[1]}
                      PreTag="div"
                      {...props}
                    >
                      {String(children).replace(/\n$/, '')}
                    </SyntaxHighlighter>
                  ) : (
                    <code className={className} {...props}>
                      {children}
                    </code>
                  );
                },
              }}
            >
              {message.content}
            </ReactMarkdown>
          </div>
        </div>
        {message.progress !== undefined && message.progress > 0 && (
          <div className="mt-2">
            <div className="flex items-center justify-between text-xs text-gray-600 dark:text-gray-400 mb-1">
              <span>Progress</span>
              <span>{Math.round(message.progress * 100)}%</span>
            </div>
            <div className="w-full bg-gray-200 dark:bg-gray-600 rounded-full h-1.5">
              <div
                className="bg-blue-600 h-1.5 rounded-full transition-all duration-300"
                style={{ width: `${message.progress * 100}%` }}
              />
            </div>
          </div>
        )}
        <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
          {message.timestamp.toLocaleTimeString()}
        </div>
      </div>
    </div>
  );
}
