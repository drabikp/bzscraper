import { Alert, Box, Button, Center, Checkbox, Group, Paper, PasswordInput, SegmentedControl, Stack, Text, TextInput, ThemeIcon, Title } from '@mantine/core';
import { IconWaveSine } from '@tabler/icons-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { useLogin } from '../api/hooks';
import { LANGUAGES, setLanguage } from '../i18n';
import { errorText } from '../lib/notify';

export function LoginPage() {
  const { t, i18n } = useTranslation();
  const login = useLogin();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(true);
  const wrong = login.error instanceof ApiError && login.error.status === 401;
  return (
    <Center mih="100dvh" p="md" bg="var(--mantine-color-default-hover)">
      <Box w="100%" maw={400}>
        <Group gap="sm" mb="lg" justify="center">
          <ThemeIcon size={44} radius="md">
            <IconWaveSine size={26} />
          </ThemeIcon>
          <Title order={1} fz={24}>
            Gig sync hub
          </Title>
        </Group>
        <Paper withBorder p="xl" shadow="sm">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              login.mutate({ username, password, remember });
            }}
          >
            <Stack>
              <Title order={2} fz={20}>
                {t('login.title')}
              </Title>
              {login.error && (
                <Alert color="red" variant="light">
                  {wrong ? t('login.wrong') : errorText(login.error)}
                </Alert>
              )}
              <TextInput label={t('login.username')} value={username} onChange={(e) => setUsername(e.currentTarget.value)}
                autoComplete="username" autoFocus required />
              <PasswordInput label={t('login.pass')} value={password} onChange={(e) => setPassword(e.currentTarget.value)}
                autoComplete="current-password" required />
              <Checkbox label={t('login.remember')} checked={remember} onChange={(e) => setRemember(e.currentTarget.checked)} />
              <Button type="submit" size="md" loading={login.isPending}>
                {t('login.signIn')}
              </Button>
            </Stack>
          </form>
        </Paper>
        <Stack align="center" mt="lg" gap="xs">
          <SegmentedControl value={i18n.language} onChange={setLanguage}
            data={LANGUAGES.map((l) => ({ value: l.code, label: l.name }))} aria-label={t('settings.language')} />
          <Text size="xs" c="dimmed">
            {t('login.hint')}
          </Text>
        </Stack>
      </Box>
    </Center>
  );
}
