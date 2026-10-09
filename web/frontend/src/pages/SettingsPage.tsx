import { Avatar, Badge, Button, Group, Paper, SegmentedControl, Stack, Switch, Text, Title, useMantineColorScheme } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { useLogout, useMe, usePlatforms, useSyncAction, useSyncStatus } from '../api/hooks';
import { PageHeader } from '../components/PageHeader';
import { LANGUAGES, setLanguage } from '../i18n';
import { clock } from '../lib/format';
import { failed } from '../lib/notify';

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Paper withBorder p="md">
      <Title order={2} fz={13} c="dimmed" tt="uppercase" mb="sm" style={{ letterSpacing: '0.06em' }}>{title}</Title>
      <Stack gap="sm">{children}</Stack>
    </Paper>
  );
}

export function SettingsPage() {
  const { t, i18n } = useTranslation();
  const { colorScheme, setColorScheme } = useMantineColorScheme();
  const me = useMe();
  const logout = useLogout();
  const platforms = usePlatforms();
  const status = useSyncStatus();
  const sync = useSyncAction();
  const paused = status.data?.paused ?? false;
  return (
    <Stack gap="md" maw={720}>
      <PageHeader title={t('settings.title')} phoneBack="/more" />
      <Section title={t('settings.language')}>
        <SegmentedControl fullWidth value={i18n.language} onChange={setLanguage} data={LANGUAGES.map((l) => ({ value: l.code, label: l.name }))} />
      </Section>
      <Section title={t('settings.appearance')}>
        <SegmentedControl fullWidth value={colorScheme} onChange={(v) => setColorScheme(v as 'light' | 'dark' | 'auto')}
          data={[{ value: 'auto', label: t('settings.auto') }, { value: 'light', label: t('settings.light') }, { value: 'dark', label: t('settings.dark') }]} />
      </Section>
      <Section title={t('settings.platforms')}>
        {(platforms.data ?? []).map((p) => {
          const breaker = status.data?.breakers.find((b) => b.platform === p.id && b.heldUntil);
          return (
            <Stack key={p.id} gap={6}>
              <Group justify="space-between">
                <Text fw={700}>{p.name}</Text>
                <Badge color={breaker ? 'orange' : 'teal'} variant="light" styles={{ label: { textTransform: 'none' } }}>
                  {breaker ? t('settings.heldBack') : t('settings.working')}
                </Badge>
              </Group>
              {breaker && (
                <Group justify="space-between" wrap="wrap" gap="xs">
                  <Text size="sm" c="orange.9">{t('inbox.heldBody', { failures: breaker.failures, time: clock(breaker.heldUntil), last: breaker.lastFailure ?? '' })}</Text>
                  <Button size="xs" onClick={() => sync.mutate({ path: `breakers/${p.id}/resume` }, { onError: failed })}>{t('inbox.resume')}</Button>
                </Group>
              )}
            </Stack>
          );
        })}
      </Section>
      <Section title={t('settings.sync')}>
        <Switch size="md" checked={paused} label={t('activity.pause')} description={t('activity.pauseHelp')}
          onChange={() => sync.mutate({ path: paused ? 'resume' : 'pause' }, { onError: failed })} />
      </Section>
      <Section title={t('settings.account')}>
        <Group justify="space-between" wrap="wrap">
          <Group gap="sm">
            <Avatar color="brand" radius="xl">{(me.data?.username ?? '?').substring(0, 2).toUpperCase()}</Avatar>
            <Text fw={600}>{t('settings.signedInAs', { name: me.data?.username })}</Text>
          </Group>
          <Button variant="default" loading={logout.isPending} onClick={() => logout.mutate()}>{t('settings.signOut')}</Button>
        </Group>
      </Section>
    </Stack>
  );
}
