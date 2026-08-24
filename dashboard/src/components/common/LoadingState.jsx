import React from 'react';

export function LoadingState({ message = 'Loading incidents...' }) {
  return (
    <div className="state-container">
      <div className="spinner" />
      <p className="state-description">{message}</p>
    </div>
  );
}
