# Pool Aim Auto Beta 0.6 — linhas brancas, colisões e pausa durante a tacada

Esta versão evolui a 0.5 com foco nos problemas relatados no teste real: projeções de tabela desalinhadas, linhas de colisão não detectadas, linhas grossas/coloridas e projeção permanecendo visível durante o movimento das bolas.

## O que mudou na 0.6

- **Linhas projetadas brancas e finas**: o overlay desenha branco nítido, com traço próximo de 1 px. O desenho começa depois do trecho que já está visível no jogo, evitando duplicar a linha nativa.
- **Duas saídas após a colisão**: o detector procura os dois ramos que saem do marcador de contato. Também trata o caso de tacada quase reta, em que a linha da bola-alvo pode ficar praticamente colinear com a linha de entrada.
- **Extensões refletidas nas tabelas**: calcula até três segmentos de rebote e interrompe a trajetória quando ela entra na região aproximada de uma caçapa, em vez de rebater em toda abertura como se fosse trilho sólido.
- **Menos auto-detecção**: antes de analisar o próximo quadro, remove da cópia de análise apenas as extensões que o próprio overlay desenhou no quadro anterior. A linha nativa curta do jogo é preservada.
- **Ocultar durante movimento**: compara quadros consecutivos na área da mesa, desconsiderando a mira e as projeções conhecidas. Se detecta movimento das bolas, esconde as projeções e só as libera depois de sete quadros estáveis consecutivos (aproximadamente 0,8 s a 120 ms por quadro). Se a linha nativa desaparece, a projeção antiga também some imediatamente.
- **Acompanhamento mais rápido**: o estabilizador dá mais peso à direção atual para reduzir o atraso quando o taco se move.
- **Mesas escuras/neutras**: há uma área de mesa aproximada de fallback quando o feltro não tem cor saturada suficiente para a detecção por matiz.
- **Bolinha 8 flutuante e toques**: mantém o ícone de bola 8 como controle de abrir/minimizar. A camada de desenho é `NOT_TOUCHABLE`; sua opacidade fica abaixo de 0,8 para reduzir o risco de bloquear toques em Android 12+, mas o comportamento deve ser conferido no aparelho.

## Limitações que continuam importantes

- A detecção é visual e experimental. O jogo pode mudar a cor/forma das guias conforme tema, brilho, resolução ou atualização.
- O detector só consegue prolongar linhas que consegue identificar na captura. Se o jogo não desenhar claramente um dos ramos de colisão, a ferramenta pode encontrar apenas um deles ou nenhum.
- Os rebotes são aproximações geométricas. As caçapas são tratadas como aberturas aproximadas; atrito, força, efeito e física interna do jogo não são simulados com precisão.
- A pausa de movimento usa diferenças de imagem na mesa e pode precisar de ajuste fino para o modelo/resolução do seu celular.
- Testes Java locais cobrem imagens sintéticas, detecção de colisão, máscaras e pausa por movimento. Eles não substituem compilar o app Android na nuvem e testar no aparelho.

## Gerar o APK pelo GitHub Actions (Windows Server 2012 R2)

1. Baixe e extraia o ZIP.
2. No repositório GitHub `8Ball`, use **Add file → Upload files** e envie o conteúdo extraído, mantendo os caminhos e substituindo arquivos com o mesmo nome.
3. Em `.github/workflows/`, mantenha apenas `blank.yml` para evitar compilações duplicadas.
4. Clique em **Commit changes**.
5. Abra **Actions** e aguarde **Build Pool Aim Auto Beta 0.6 APK**.
6. Na execução concluída, abra **Summary → Artifacts** e baixe `pool-aim-auto-v06-debug-apk`.
7. Extraia o ZIP do artefato e instale `app-debug.apk` no Android. Se a assinatura de depuração for diferente da versão anterior, desinstale a anterior antes de instalar a 0.6.

O workflow usa Java 17, Android SDK 35 e Gradle 8.9 na nuvem. Não é necessário instalar Android Studio no Windows Server.
