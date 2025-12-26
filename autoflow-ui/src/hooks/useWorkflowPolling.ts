import { useEffect, useRef } from 'react';
import { workflowApi } from '../services/api';
import type { WorkflowStatusResponse } from '../types/workflow';

interface UseWorkflowPollingOptions {
  conversationId: string | null;
  enabled: boolean;
  interval?: number;
  onStatusUpdate: (status: WorkflowStatusResponse) => void;
  onError?: (error: Error) => void;
}

/**
 * Hook for polling workflow status
 * Polls every 2 seconds while workflow is RUNNING or WAITING_FOR_DEVELOPER
 */
export function useWorkflowPolling({
  conversationId,
  enabled,
  interval = 2000,
  onStatusUpdate,
  onError,
}: UseWorkflowPollingOptions) {
  const intervalRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (!conversationId || !enabled) {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      return;
    }

    const pollStatus = async () => {
      try {
        const status = await workflowApi.getStatus(conversationId);
        onStatusUpdate(status);

        // Stop polling if workflow is complete or failed
        if (status.status === 'COMPLETED' || status.status === 'FAILED') {
          if (intervalRef.current) {
            clearInterval(intervalRef.current);
            intervalRef.current = null;
          }
        }
      } catch (error) {
        console.error('Failed to poll status:', error);
        onError?.(error as Error);
      }
    };

    // Poll immediately
    pollStatus();

    // Set up interval polling
    intervalRef.current = setInterval(pollStatus, interval);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [conversationId, enabled, interval, onStatusUpdate, onError]);
}
