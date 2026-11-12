<script lang="ts">
  import { store, type Layout } from '../lib/store.svelte';
  import Icon from '../lib/Icon.svelte';
  import Sidebar from './Sidebar.svelte';
  import ArticleList from './ArticleList.svelte';
  import ArticleView from './ArticleView.svelte';
  import CombinedView from './CombinedView.svelte';
  import Welcome from './Welcome.svelte';

  const mode = $derived(store.settings.viewMode);
  const empty = $derived(store.ready && store.tree.feeds.length === 0);

  function resize(key: keyof Layout, axis: 'x' | 'y', min: number, max: number) {
    return (e: PointerEvent) => {
      if (e.button !== 0) return;
      e.preventDefault();
      const handle = e.currentTarget as HTMLElement;
      const start = axis === 'x' ? e.clientX : e.clientY;
      const initial = store.layout[key];
      handle.setPointerCapture(e.pointerId);
      document.body.classList.add(`resizing-${axis}`);
      const move = (ev: PointerEvent) => {
        const delta = (axis === 'x' ? ev.clientX : ev.clientY) - start;
        store.layout[key] = Math.round(Math.max(min, Math.min(max, initial + delta)));
      };
      const up = () => {
        handle.removeEventListener('pointermove', move);
        handle.removeEventListener('pointerup', up);
        handle.removeEventListener('pointercancel', up);
        document.body.classList.remove(`resizing-${axis}`);
        store.saveLayout();
      };
      handle.addEventListener('pointermove', move);
      handle.addEventListener('pointerup', up);
      handle.addEventListener('pointercancel', up);
    };
  }
</script>

<div
  class="workspace"
  class:sidebar-open={store.sidebarOpen}
  class:article-open={store.selectedId != null}
  style:--sidebar-w="{store.layout.sidebar}px"
  style:--list-w="{store.layout.list}px"
  style:--list-h="{store.layout.listHeight}px"
>
  {#if store.sidebarOpen}
    <div class="mobile-scrim" role="presentation" onclick={() => (store.sidebarOpen = false)}></div>
  {/if}
  <div class="sidebar-drawer">
    <Sidebar />
  </div>
  <div class="resizer x" role="separator" aria-orientation="vertical" onpointerdown={resize('sidebar', 'x', 200, 420)}></div>

  {#if empty}
    <Welcome />
  {:else if mode === 'combined'}
    <CombinedView />
  {:else}
    <div class="reading {mode}">
      <ArticleList />
      <div
        class="resizer {mode === 'widescreen' ? 'x' : 'y'}"
        role="separator"
        onpointerdown={mode === 'widescreen'
          ? resize('list', 'x', 300, 620)
          : resize('listHeight', 'y', 160, 640)}
      ></div>
      <div class="preview">
        {#if store.selectedId != null}
          <ArticleView articleId={store.selectedId} mode="preview" active={store.activeTabId === 'main'} />
        {:else}
          <div class="placeholder">
            <div class="placeholder-icon"><Icon name="newspaper" size={30} stroke={1.4} /></div>
            <p>Select an article to read it here</p>
            <p class="keys"><kbd>J</kbd><kbd>K</kbd> to move · <kbd>N</kbd> next unread · <kbd>?</kbd> all shortcuts</p>
          </div>
        {/if}
      </div>
    </div>
  {/if}
</div>

<style>
  .workspace {
    display: grid;
    grid-template-columns: var(--sidebar-w) 0 minmax(0, 1fr);
    height: 100%;
  }
  .sidebar-drawer {
    display: grid;
    min-width: 0;
    min-height: 0;
    height: 100%;
  }
  .mobile-scrim {
    display: none;
  }
  .reading {
    display: grid;
    min-width: 0;
    min-height: 0;
  }
  .reading.widescreen {
    grid-template-columns: var(--list-w) 0 minmax(0, 1fr);
  }
  .reading.normal {
    grid-template-rows: var(--list-h) 0 minmax(0, 1fr);
  }
  .preview {
    position: relative;
    min-width: 0;
    min-height: 0;
    background: var(--bg-content);
  }
  .resizer {
    position: relative;
    z-index: 5;
  }
  .resizer::after {
    content: '';
    position: absolute;
    transition: background 0.15s;
  }
  .resizer.x::after {
    top: 0;
    bottom: 0;
    left: -3px;
    width: 6px;
    cursor: col-resize;
  }
  .resizer.y::after {
    left: 0;
    right: 0;
    top: -3px;
    height: 6px;
    cursor: row-resize;
  }
  .resizer:hover::after {
    background: var(--accent-ring);
  }
  :global(body.resizing-x),
  :global(body.resizing-x *) {
    cursor: col-resize !important;
  }
  :global(body.resizing-y),
  :global(body.resizing-y *) {
    cursor: row-resize !important;
  }
  .placeholder {
    height: 100%;
    display: grid;
    place-content: center;
    justify-items: center;
    gap: 6px;
    color: var(--text-3);
    text-align: center;
  }
  .placeholder p {
    margin: 0;
  }
  .placeholder-icon {
    display: grid;
    place-items: center;
    width: 64px;
    height: 64px;
    border-radius: 18px;
    background: var(--bg-muted);
    margin-bottom: 8px;
  }
  .keys {
    margin-top: 10px !important;
    font-size: 12px;
    display: flex;
    align-items: center;
    gap: 4px;
  }
  @media (max-width: 860px) {
    .workspace {
      grid-template-columns: minmax(0, 1fr);
      grid-template-rows: minmax(0, 1fr);
      position: relative;
    }
    .resizer {
      display: none;
    }
    .mobile-scrim {
      display: block;
      position: fixed;
      inset: 0;
      z-index: 490;
      background: var(--scrim);
      -webkit-backdrop-filter: blur(2px);
      backdrop-filter: blur(2px);
    }
    .sidebar-drawer {
      position: fixed;
      top: 0;
      bottom: 0;
      left: 0;
      width: min(304px, 84vw);
      z-index: 500;
      transform: translateX(-104%);
      transition: transform 0.22s var(--ease-out);
      box-shadow: var(--shadow-lg);
    }
    .workspace.sidebar-open .sidebar-drawer {
      transform: translateX(0);
    }
    .reading.widescreen,
    .reading.normal {
      grid-template-columns: minmax(0, 1fr);
      grid-template-rows: minmax(0, 1fr);
    }
    .workspace:not(.article-open) .preview {
      display: none;
    }
    .workspace.article-open .reading > :global(.list-pane) {
      display: none;
    }
  }
</style>
