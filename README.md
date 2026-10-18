# Pool Aim Auto Beta 0.4 — detecção experimental da mira

Esta versão atualiza o projeto Android enviado para corrigir os principais problemas observados no vídeo de teste.

## O que mudou na versão 0.4

- O detector agora procura primeiro uma região branca compacta (provável bola branca) e avalia as linhas claras que saem dessa região. Isso reduz a chance de seguir uma linha longa que esteja em outra parte da mesa.
- A linha projetada é **ciano-clara, contínua e fina**, para diferenciá-la da linha branca original e evitar que o detector reconheça a própria projeção como se fosse a mira do jogo.
- A captura é analisada com intervalo mínimo de aproximadamente 120 ms, em vez de 650 ms, para acompanhar melhor o movimento do taco.
- A estabilização aceita mudanças de ângulo mais rapidamente e descarta uma detecção antiga após um intervalo menor.
- A camada de desenho permanece `NOT_TOUCHABLE` e usa opacidade de janela reduzida para diminuir a possibilidade de bloquear os toques no jogo no Android 12 ou superior.
- A bolinha 8 continua como botão flutuante: toque para abrir/fechar o menu e arraste para reposicioná-la.
- O status do menu reflete se uma linha foi encontrada antes de o menu ser aberto.

## Limitações importantes

- A detecção é heurística e experimental. Pode falhar dependendo da resolução, cores da mesa, animações, compressão da imagem e aparência da linha original.
- A projeção geométrica reflete a direção detectada nas bordas da mesa. Ela **não** calcula com precisão colisões entre bolas, força, velocidade, efeito, atrito nem o destino real da bola após uma tacada.
- O APK desta versão ainda precisa ser compilado pelo GitHub Actions e testado no celular. Os testes locais realizados aqui cobrem o detector Java com imagens sintéticas e quadros do vídeo enviado, mas não substituem o teste do APK no aparelho.

## Como compilar no GitHub Actions

1. Envie os arquivos deste projeto ao repositório, mantendo a estrutura de pastas.
2. Em `.github/workflows/`, mantenha apenas um workflow de compilação para evitar duas execuções duplicadas. Este projeto usa `blank.yml`, para substituir o workflow simples que você já usou.
3. Abra **Actions → Build Pool Aim Auto Beta APK** e aguarde a execução.
4. Na página da execução, abra **Summary → Artifacts** e baixe `pool-aim-auto-v04-debug-apk`.
5. Extraia o ZIP baixado e instale `app-debug.apk` no Android.

O workflow usa Java 17, Android SDK 35 e Gradle 8.9. Não é necessário instalar Android Studio no Windows Server para compilar na nuvem.
