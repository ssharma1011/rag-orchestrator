import axios from 'axios';
import type { WorkflowRequest, WorkflowStatusResponse, RespondRequest } from '../types/workflow';

const API_BASE = '/api/v1';

const apiClient = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const workflowApi = {
  /**
   * Start a new workflow
   * @returns 202 Accepted with initial workflow status
   */
  async startWorkflow(request: WorkflowRequest): Promise<WorkflowStatusResponse> {
    const { data } = await apiClient.post<WorkflowStatusResponse>('/workflows/start', request);
    return data;
  },

  /**
   * Get current workflow status (for polling)
   * @param conversationId Conversation ID
   * @returns Current workflow status
   */
  async getStatus(conversationId: string): Promise<WorkflowStatusResponse> {
    const { data } = await apiClient.get<WorkflowStatusResponse>(`/workflows/${conversationId}/status`);
    return data;
  },

  /**
   * Respond to workflow questions
   * @param conversationId Conversation ID
   * @param response User's response to questions
   * @returns Updated workflow status (may trigger async execution)
   */
  async respond(conversationId: string, response: string): Promise<WorkflowStatusResponse> {
    const { data } = await apiClient.post<WorkflowStatusResponse>(
      `/workflows/${conversationId}/respond`,
      { response } as RespondRequest
    );
    return data;
  },
};
