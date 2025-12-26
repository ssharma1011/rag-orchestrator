import axios from 'axios';
import type { AgentMetrics, ConversationMetrics, SystemMetrics } from '../types/metrics';

const API_BASE = '/api/v1';

const apiClient = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const metricsApi = {
  /**
   * Get metrics for a specific conversation
   */
  async getConversationMetrics(conversationId: string): Promise<ConversationMetrics> {
    const { data } = await apiClient.get<ConversationMetrics>(`/metrics/conversation/${conversationId}`);
    return data;
  },

  /**
   * Get metrics for a specific agent across all conversations
   */
  async getAgentMetrics(agentName: string): Promise<AgentMetrics> {
    const { data } = await apiClient.get<AgentMetrics>(`/metrics/agent/${agentName}`);
    return data;
  },

  /**
   * Get system-wide metrics
   */
  async getSystemMetrics(): Promise<SystemMetrics> {
    const { data } = await apiClient.get<SystemMetrics>('/metrics/system');
    return data;
  },

  /**
   * Reset all metrics
   */
  async resetMetrics(): Promise<void> {
    await apiClient.post('/metrics/reset');
  },
};
