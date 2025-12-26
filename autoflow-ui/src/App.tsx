import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { Header } from './components/Header';
import { ChatContainer } from './components/ChatContainer';
import { MetricsDashboard } from './components/MetricsDashboard';

function App() {
  return (
    <Router>
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <Header />
        <Routes>
          <Route path="/" element={<ChatContainer />} />
          <Route path="/metrics" element={<MetricsDashboard />} />
        </Routes>
      </div>
    </Router>
  );
}

export default App;
