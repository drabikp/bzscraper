import { Center, Loader } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router';
import { whenUnauthorized } from './api/client';
import { useMe } from './api/hooks';
import { AppLayout } from './layout/AppLayout';
import { ActivityPage } from './pages/ActivityPage';
import { CalendarPage } from './pages/CalendarPage';
import { CheckPage } from './pages/CheckPage';
import { GigFormPage } from './pages/GigFormPage';
import { GigPage } from './pages/GigPage';
import { GigsPage } from './pages/GigsPage';
import { ImportPage } from './pages/ImportPage';
import { InboxPage } from './pages/InboxPage';
import { LoginPage } from './pages/LoginPage';
import { MorePage } from './pages/MorePage';
import { SettingsPage } from './pages/SettingsPage';

/** Signed in → the app; otherwise the sign-in page (the API answers 401 when the session ends). */
export function App() {
  const me = useMe();
  const client = useQueryClient();
  useEffect(() => whenUnauthorized(() => client.setQueryData(['me'], null)), [client]);
  if (me.isLoading) {
    return (
      <Center mih="100dvh">
        <Loader />
      </Center>
    );
  }
  if (!me.data) return <LoginPage />;
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<GigsPage />} />
          <Route path="gig/new" element={<GigFormPage mode="new" />} />
          <Route path="gig/:id" element={<GigPage />} />
          <Route path="gig/:id/edit" element={<GigFormPage mode="edit" />} />
          <Route path="inbox" element={<InboxPage />} />
          <Route path="calendar" element={<CalendarPage />} />
          <Route path="calendar/:eventId/add" element={<GigFormPage mode="calendar" />} />
          <Route path="more" element={<MorePage />} />
          <Route path="activity" element={<ActivityPage />} />
          <Route path="import" element={<ImportPage />} />
          <Route path="check" element={<CheckPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
