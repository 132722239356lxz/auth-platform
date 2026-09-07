<template>
  <div class="kb-page app-container">
    <div class="page-header">
      <div class="header-title">
        <el-icon class="title-icon"><Collection /></el-icon>
        <span>知识库管理</span>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="handleCreateKb">
          新建知识库
        </el-button>
        <el-button :icon="Refresh" :loading="loading" @click="loadData">
          刷新
        </el-button>
      </div>
    </div>

    <div class="query-bar">
      <el-input
        v-model="kbSearch"
        placeholder="搜索知识库名称 / 描述"
        clearable
        :prefix-icon="Search"
        class="query-input"
        @input="handleKbSearch"
      />
      <el-select
        v-model="docTypeFilter"
        placeholder="文档类型"
        clearable
        class="query-select"
        @change="handleDocFilterChange"
      >
        <el-option label="纯文本" value="TEXT" />
        <el-option label="Markdown" value="MARKDOWN" />
        <el-option label="HTML" value="HTML" />
        <el-option label="JSON" value="JSON" />
        <el-option label="CSV" value="CSV" />
        <el-option label="代码" value="CODE" />
      </el-select>
      <el-select
        v-model="splitterFilter"
        placeholder="切片策略"
        clearable
        class="query-select"
        @change="handleDocFilterChange"
      >
        <el-option label="自动推断" value="AUTO" />
        <el-option label="段落语义" value="PARAGRAPH" />
        <el-option label="Markdown结构" value="MARKDOWN" />
        <el-option label="HTML标签" value="HTML" />
        <el-option label="JSON节点" value="JSON" />
        <el-option label="CSV行" value="CSV" />
        <el-option label="代码函数" value="CODE" />
        <el-option label="固定字符" value="CHARACTER" />
      </el-select>
      <el-button :icon="RefreshRight" @click="resetQuery">重置</el-button>
    </div>

    <div class="page-body">
      <el-card class="kb-card" shadow="never">
        <template #header>
          <div class="card-header">
            <div class="section-title-bar">
              <el-icon><FolderOpened /></el-icon>
              <span>知识库列表</span>
              <el-tag v-if="stats" type="primary" size="small">{{ filteredKbList.length }} 个知识库</el-tag>
            </div>
          </div>
        </template>

        <div v-loading="loading" class="table-wrapper">
          <el-table
            ref="kbTableRef"
            :data="pagedKbList"
            highlight-current-row
            row-key="name"
            height="100%"
            @current-change="handleKbSelectChange"
            @row-click="handleKbRowClick"
          >
            <el-table-column type="index" width="50" align="center" />
            <el-table-column label="知识库名称" prop="name" min-width="120" show-overflow-tooltip />
            <el-table-column label="描述" prop="description" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="text-secondary">{{ row.description || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="文档数" prop="docCount" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ docCountForKb(row.name) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" align="center" fixed="right">
              <template #default="{ row }">
                <div class="operation-cell">
                  <el-button type="primary" link :icon="Edit" @click.stop="handleEditKb(row)" />
                  <el-popconfirm
                    title="确定删除该知识库吗？"
                    confirm-button-text="删除"
                    cancel-button-text="取消"
                    confirm-button-type="danger"
                    @confirm="handleDeleteKb(row)"
                  >
                    <template #reference>
                      <el-button type="danger" link :icon="Delete" @click.stop />
                    </template>
                  </el-popconfirm>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-pagination
          v-model:current-page="kbPagination.page"
          v-model:page-size="kbPagination.pageSize"
          :total="filteredKbList.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          class="pagination-bar"
          background
          small
        />
      </el-card>

      <div class="right-panel">
        <el-card class="doc-card" shadow="never">
          <template #header>
            <div class="card-header">
              <div class="section-title-bar">
                <el-icon><Document /></el-icon>
                <span>文档：{{ selectedKb?.name || '请选择知识库' }}</span>
                <el-tag v-if="stats" type="primary" size="small">
                  {{ filteredDocList.length }} 文档 · {{ totalChunkCount }} 分块
                </el-tag>
              </div>
              <div class="header-actions">
                <el-button size="small" :icon="Search" @click="openSearchDrawer">
                  RAG 查询
                </el-button>
                <el-badge :value="runningTaskCount" :hidden="runningTaskCount === 0" class="task-badge">
                  <el-button size="small" :icon="Timer" @click="progressDrawerVisible = true">
                    上传进度
                  </el-button>
                </el-badge>
                <el-button size="small" type="primary" :icon="Upload" @click="handleAddDoc">
                  上传文档
                </el-button>
              </div>
            </div>
          </template>

          <div v-loading="docLoading" class="table-wrapper">
            <el-table
              v-if="selectedKb"
              ref="docTableRef"
              :data="pagedDocList"
              highlight-current-row
              row-key="id"
              height="100%"
              @current-change="handleDocSelectChange"
              @row-click="handleDocRowClick"
            >
              <el-table-column type="index" width="50" align="center" />
              <el-table-column label="文档标题" prop="title" min-width="160" show-overflow-tooltip />
              <el-table-column label="文件名" prop="fileName" min-width="140" show-overflow-tooltip />
              <el-table-column label="类型" prop="contentType" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" type="info">{{ row.contentType || 'TEXT' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="切片策略" prop="splitterType" width="120" align="center" />
              <el-table-column label="切片大小" prop="chunkSize" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" type="info">{{ row.chunkSize || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="重叠数" prop="overlap" width="90" align="center">
                <template #default="{ row }">
                  <el-tag size="small" type="info">{{ row.overlap || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="分块数" prop="chunkCount" width="90" align="center">
                <template #default="{ row }">
                  <el-tag size="small">{{ row.chunkCount || 0 }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" prop="status" width="100" align="center">
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :type="row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'"
                  >
                    {{ formatDocStatus(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="110" align="center" fixed="right">
                <template #default="{ row }">
                  <div class="operation-cell">
                    <el-button type="primary" link :icon="Edit" @click.stop="handleEditDoc(row)" />
                    <el-popconfirm
                      title="确定删除该文档吗？"
                      confirm-button-text="删除"
                      cancel-button-text="取消"
                      confirm-button-type="danger"
                      @confirm="handleDeleteDoc(row)"
                    >
                      <template #reference>
                        <el-button type="danger" link :icon="Delete" @click.stop />
                      </template>
                    </el-popconfirm>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <el-empty v-else description="请在左侧选择知识库" :image-size="120" />
          </div>

          <el-pagination
            v-if="selectedKb"
            v-model:current-page="docPagination.page"
            v-model:page-size="docPagination.pageSize"
            :total="filteredDocList.length"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            class="pagination-bar"
            background
            small
          />
        </el-card>

        <el-card class="chunk-card" shadow="never">
          <template #header>
            <div class="card-header">
              <div class="section-title-bar">
                <el-icon><Tickets /></el-icon>
                <span>文档分块</span>
                <el-tag v-if="selectedDoc" type="primary" size="small">{{ selectedDoc.title }}</el-tag>
              </div>
              <el-button
                v-if="selectedDoc"
                size="small"
                :icon="RefreshRight"
                :loading="chunkLoading"
                @click="loadChunks(selectedDoc)"
              >
                刷新分块
              </el-button>
            </div>
          </template>

          <div v-loading="chunkLoading" class="table-wrapper">
            <el-table v-if="selectedDoc" :data="pagedChunkList" height="100%">
              <el-table-column type="index" width="50" align="center" />
              <el-table-column label="分块序号" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" type="info">#{{ (row.chunkIndex ?? 0) + 1 }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="Tokens" prop="tokenCount" width="90" align="center">
                <template #default="{ row }">
                  <el-tag size="small" type="info">{{ row.tokenCount || 0 }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="分块内容" min-width="260" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="chunk-text-preview link-text" @click="openChunkDialog(row)">
                    {{ row.chunkText || '-' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="分块时间" width="160" align="center">
                <template #default="{ row }">
                  <span class="text-secondary">{{ formatTime(row.createdAt) }}</span>
                </template>
              </el-table-column>
            </el-table>

            <el-alert
              v-else
              title="请先选择文档以查看分块内容"
              type="info"
              :closable="false"
              show-icon
            />
            </div>

            <el-pagination
              v-if="selectedDoc"
              v-model:current-page="chunkPagination.page"
              v-model:page-size="chunkPagination.pageSize"
              :total="chunkList.length"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              class="pagination-bar"
              background
              small
            />
          </el-card>
        </div>
      </div>

      <!-- 分块详情弹窗 -->
      <el-dialog
        v-model="chunkDialogVisible"
        title="分块详情"
        width="720px"
        destroy-on-close
        :close-on-click-modal="true"
      >
        <div v-if="activeChunk" class="chunk-detail">
          <div class="chunk-detail-meta">
            <el-descriptions :column="3" size="small" border>
              <el-descriptions-item label="分块序号">#{{ (activeChunk.chunkIndex ?? 0) + 1 }}</el-descriptions-item>
              <el-descriptions-item label="Tokens">{{ activeChunk.tokenCount || 0 }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatTime(activeChunk.createdAt) }}</el-descriptions-item>
            </el-descriptions>
          </div>

          <div class="chunk-detail-content">
            <div class="chunk-detail-toolbar">
              <el-tag size="small" type="primary">{{ chunkTag }}</el-tag>
              <el-button size="small" :icon="DocumentCopy" @click="copyChunkText">复制内容</el-button>
            </div>

            <div class="chunk-text-block">{{ activeChunk.chunkText }}</div>
          </div>
        </div>

        <template #footer>
          <el-button @click="chunkDialogVisible = false">关闭</el-button>
        </template>
      </el-dialog>

    <!-- 知识库编辑弹窗 -->
    <el-dialog
      v-model="kbDialogVisible"
      :title="kbForm.id ? '编辑知识库' : '新建知识库'"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
      class="compact-dialog"
    >
      <el-form ref="kbFormRef" :model="kbForm" :rules="kbRules" label-width="80px" class="dialog-form">
        <el-form-item label="名称" prop="name">
          <el-input v-model="kbForm.name" placeholder="知识库名称" clearable />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="kbForm.description"
            type="textarea"
            :rows="3"
            placeholder="知识库描述（可选）"
            resize="none"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kbDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="kbSubmitting" @click="submitKbForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 文档编辑弹窗 -->
    <el-dialog
      v-model="docDialogVisible"
      :title="docForm.id ? '编辑文档' : '上传文档'"
      width="860px"
      destroy-on-close
      :close-on-click-modal="false"
      class="compact-dialog doc-dialog"
      @closed="resetDocForm"
    >
      <el-form ref="docFormRef" :model="docForm" :rules="docRules" label-width="90px" class="dialog-form doc-form">
        <div class="config-section">
          <div class="section-header">
            <el-icon class="section-icon"><Setting /></el-icon>
            <span class="section-title">基础配置</span>
          </div>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="知识库" prop="kbName">
                <el-select v-model="docForm.kbName" placeholder="选择知识库" clearable class="w-full">
                  <el-option
                    v-for="kb in knowledgeBases"
                    :key="kb.name"
                    :value="kb.name"
                    :label="kb.name"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="切片策略" prop="splitterType">
                <el-select v-model="docForm.splitterType" class="w-full">
                  <el-option value="AUTO" label="自动推断" />
                  <el-option value="PARAGRAPH" label="段落语义" />
                  <el-option value="MARKDOWN" label="Markdown结构" />
                  <el-option value="HTML" label="HTML标签" />
                  <el-option value="JSON" label="JSON节点" />
                  <el-option value="CSV" label="CSV行" />
                  <el-option value="CODE" label="代码函数" />
                  <el-option value="CHARACTER" label="固定字符" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16" class="mt-2">
            <el-col :span="12">
              <el-form-item label="切片大小" prop="chunkSize">
                <el-input-number
                  v-model="docForm.chunkSize"
                  :min="100"
                  :max="4000"
                  controls-position="right"
                  class="w-full"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="重叠数" prop="overlap">
                <el-input-number
                  v-model="docForm.overlap"
                  :min="0"
                  :max="500"
                  controls-position="right"
                  class="w-full"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <el-tabs v-model="docTab" type="border-card" class="doc-tabs">
          <el-tab-pane label="手动录入" name="manual">
            <el-row :gutter="16">
              <el-col :span="16">
                <el-form-item label="文档标题" prop="title">
                  <el-input v-model="docForm.title" placeholder="请输入文档标题" clearable />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="文档类型" prop="contentType">
                  <el-select v-model="docForm.contentType" class="w-full">
                    <el-option value="TEXT" label="纯文本" />
                    <el-option value="MARKDOWN" label="Markdown" />
                    <el-option value="HTML" label="HTML" />
                    <el-option value="JSON" label="JSON" />
                    <el-option value="CSV" label="CSV" />
                    <el-option value="CODE" label="代码" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="文档内容" prop="content" class="editor-item">
              <MdEditor
                v-if="docTab === 'manual'"
                v-model="docForm.content"
                class="md-editor"
                placeholder="请输入文档内容，支持 Markdown 格式"
                :toolbars="(toolbars as any)"
                :preview="false"
              />
            </el-form-item>
          </el-tab-pane>

          <el-tab-pane label="文件上传" name="upload">
            <el-form-item label="选择文件" prop="files">
              <el-upload
                ref="uploadRef"
                drag
                action="#"
                :auto-upload="false"
                :multiple="true"
                :on-change="handleUploadChange"
                :on-remove="handleUploadRemove"
                :file-list="uploadFileItems"
                accept=".txt,.md,.html,.json,.csv,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx"
                class="upload-dragger"
              >
                <el-icon class="upload-icon"><UploadFilled /></el-icon>
                <div class="upload-text">
                  <span class="upload-title">点击或拖拽文件到此处上传</span>
                  <span class="upload-tips">支持 TXT、Markdown、PDF、Word、Excel、PPT 等常见格式</span>
                </div>
              </el-upload>
              <div class="upload-limits">
                单次最多上传 <b>{{ MAX_FILE_COUNT }}</b> 个文件，单个文件不超过 <b>{{ formatFileSize(MAX_FILE_SIZE) }}</b>
              </div>
            </el-form-item>

            <div v-if="uploadFileItems.length > 0" class="upload-file-list">
              <div class="list-header">
                <span class="list-title">已选文件 ({{ uploadFileItems.length }})</span>
                <el-button type="danger" link size="small" :disabled="isUploading" @click="clearUploadFiles">
                  清空
                </el-button>
              </div>
              <el-table :data="uploadFileItems" size="small" :show-header="true" border>
                <el-table-column type="index" width="50" align="center" />
                <el-table-column label="文件名" prop="name" min-width="180" show-overflow-tooltip />
                <el-table-column label="大小" width="110" align="center">
                  <template #default="{ row }">
                    {{ formatFileSize(row.size) }}
                  </template>
                </el-table-column>
                <el-table-column label="文档类型" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" type="info" effect="plain">{{ formatContentType(row.contentType) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="切片策略" width="110" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" type="info" effect="plain">{{ formatSplitterType(row.splitterType) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="130" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.status === 'ready'" size="small" type="info"><el-icon><Document /></el-icon> 待上传</el-tag>
                    <el-tag v-else-if="row.status === 'uploading'" size="small" type="warning"><el-icon class="is-loading"><Loading /></el-icon> 上传中</el-tag>
                    <el-tag v-else-if="row.status === 'processing'" size="small" type="warning"><el-icon class="is-loading"><Loading /></el-icon> 处理中</el-tag>
                    <el-tag v-else-if="row.status === 'success'" size="small" type="success"><el-icon><CircleCheck /></el-icon> 成功</el-tag>
                    <el-tag v-else-if="row.status === 'error'" size="small" type="danger"><el-icon><CircleClose /></el-icon> 失败</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="进度" width="160" align="center">
                  <template #default="{ row }">
                    <el-progress
                      :percentage="row.percentage || 0"
                      :status="row.status === 'error' ? 'exception' : row.status === 'success' ? 'success' : undefined"
                      size="small"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80" align="center">
                  <template #default="{ row, $index }">
                    <el-button
                      type="danger"
                      link
                      size="small"
                      :disabled="isUploading"
                      @click="removeUploadFile($index)"
                    >
                      移除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-alert
                v-if="isUploading"
                title="文件正在提交上传，请勿关闭弹窗..."
                type="info"
                show-icon
                :closable="false"
                class="upload-tip"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <template #footer>
        <el-button @click="docDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="docSubmitting" @click="submitDocForm">
          {{ docForm.id ? '保存' : '提交' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 知识库查询抽屉 -->
    <el-drawer
      v-model="searchDrawerVisible"
      title="知识库查询"
      size="600px"
      destroy-on-close
      :close-on-click-modal="true"
    >
      <div class="rag-search">
        <el-alert
          v-if="!searchForm.kbName"
          type="info"
          :closable="false"
          show-icon
          class="mb-16"
          title="未指定知识库时将检索全部知识库（所有切片）"
        />

        <el-form label-width="80px">
          <el-form-item label="目标知识库">
            <el-select v-model="searchForm.kbName" placeholder="留空 = 全库检索" clearable class="w-full">
              <el-option
                v-for="kb in knowledgeBases"
                :key="kb.name"
                :value="kb.name"
                :label="kb.name"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="检索数量">
            <el-slider v-model="searchForm.topK" :min="1" :max="20" show-stops :step="1" show-tooltip />
          </el-form-item>
          <el-form-item label="查询内容">
            <el-input
              v-model="searchForm.query"
              type="textarea"
              :rows="4"
              placeholder="输入你想查询的内容..."
              resize="none"
            />
          </el-form-item>
        </el-form>

        <el-tabs v-model="searchTab" class="search-tabs">
          <!-- RAG 查询 -->
          <el-tab-pane label="RAG 查询" name="rag">
            <div class="tab-actions">
              <el-button type="primary" :icon="Search" :loading="ragLoading" @click="executeRagSearch">
                执行 RAG 查询
              </el-button>
              <span class="tab-hint">向量召回 + LLM 生成回答</span>
            </div>
            <div v-if="ragResult" class="rag-result">
              <el-divider />
              <h4>AI 回答</h4>
              <el-card class="answer-card" shadow="never">
                <pre>{{ ragResult.answer || '未生成回答' }}</pre>
              </el-card>
              <h4 class="mt-16">检索上下文（{{ ragResult.context?.length || 0 }}）</h4>
              <el-card
                v-for="(ctx, idx) in ragResult.context || []"
                :key="idx"
                class="context-card"
                shadow="never"
              >
                <div class="context-header">
                  <el-tag size="small" type="primary">#{{ idx + 1 }}</el-tag>
                  <span class="context-score" v-if="ctx.score !== undefined">相似度: {{ (ctx.score * 100).toFixed(1) }}%</span>
                </div>
                <p class="context-text">{{ ctx.text }}</p>
              </el-card>
            </div>
          </el-tab-pane>

          <!-- BM25 查询 -->
          <el-tab-pane label="BM25 查询" name="bm25">
            <div class="tab-actions">
              <el-button type="warning" :icon="Search" :loading="bm25Loading" @click="executeBm25Search">
                执行 BM25 查询
              </el-button>
              <span class="tab-hint">Lucene 全文检索（关键词匹配，始终全库）</span>
            </div>
            <div v-if="bm25Result" class="search-result">
              <el-divider />
              <h4>命中结果（{{ bm25Result.results?.length || 0 }}）</h4>
              <el-card
                v-for="(item, idx) in bm25Result.results || []"
                :key="idx"
                class="context-card"
                shadow="never"
              >
                <div class="context-header">
                  <el-tag size="small" type="warning">#{{ idx + 1 }}</el-tag>
                  <span class="context-score" v-if="item.score !== undefined">BM25 分: {{ item.score.toFixed(3) }}</span>
                </div>
                <p class="context-title" v-if="item.title">{{ item.title }}</p>
                <p class="context-text" v-html="item.highlight || item.content"></p>
              </el-card>
              <el-empty v-if="!(bm25Result.results?.length)" description="无命中结果" />
            </div>
          </el-tab-pane>

          <!-- 向量索引查询 -->
          <el-tab-pane label="向量索引查询" name="vector">
            <div class="tab-actions">
              <el-button type="success" :icon="Search" :loading="vectorLoading" @click="executeVectorSearch">
                执行向量查询
              </el-button>
              <span class="tab-hint">向量相似度召回（余弦）</span>
            </div>
            <div v-if="vectorResult" class="search-result">
              <el-divider />
              <h4>相似切片（{{ vectorResult.context?.length || vectorResult.results?.length || 0 }}）</h4>
              <template v-if="vectorResult.context?.length">
                <el-card
                  v-for="(ctx, idx) in vectorResult.context || []"
                  :key="idx"
                  class="context-card"
                  shadow="never"
                >
                  <div class="context-header">
                    <el-tag size="small" type="success">#{{ idx + 1 }}</el-tag>
                    <span class="context-score" v-if="ctx.score !== undefined">余弦相似度: {{ (ctx.score * 100).toFixed(1) }}%</span>
                  </div>
                  <p class="context-text">{{ ctx.text }}</p>
                </el-card>
              </template>
              <template v-else>
                <el-card
                  v-for="(item, idx) in vectorResult.results || []"
                  :key="idx"
                  class="context-card"
                  shadow="never"
                >
                  <div class="context-header">
                    <el-tag size="small" type="success">#{{ idx + 1 }}</el-tag>
                    <span class="context-score" v-if="item.score !== undefined">余弦相似度: {{ (item.score * 100).toFixed(1) }}%</span>
                  </div>
                  <p class="context-title" v-if="item.title">{{ item.title }}</p>
                  <p class="context-text">{{ item.content }}</p>
                </el-card>
              </template>
              <el-empty v-if="!(vectorResult.context?.length || vectorResult.results?.length)" description="无相似切片" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <!-- 上传进度抽屉 -->
    <el-drawer
      v-model="progressDrawerVisible"
      title="上传进度"
      size="520px"
      destroy-on-close
    >
      <template #header>
        <div class="drawer-header">
          <span>上传进度</span>
          <span class="drawer-count">共 {{ progressItems.length }} 个 · 处理中 {{ progressProcessingCount }} 个</span>
          <el-button
            v-if="finishedTaskCount > 0"
            size="small"
            link
            type="primary"
            @click="clearFinishedTasks"
          >
            清空已完成
          </el-button>
        </div>
      </template>
      <div class="progress-list">
        <div v-if="progressItems.length === 0" class="empty-placeholder">
          <el-empty description="暂无正在处理的任务" :image-size="100" />
        </div>

        <div v-for="item in progressItems" :key="item.uid || item.name" class="task-item">
          <div class="task-header">
            <div class="task-file">
              <el-icon><Document /></el-icon>
              <el-tooltip :content="item.fileName" placement="top">
                <span class="filename">{{ item.fileName }}</span>
              </el-tooltip>
            </div>
            <el-tag :type="taskStatusType(item.status)" size="small">
              {{ formatTaskStatus(item.status) }}
            </el-tag>
          </div>
          <div class="task-meta">
            <span>类型: {{ formatContentType(item.contentType) }}</span>
            <span>切片: {{ formatSplitterType(item.splitterType) }}</span>
            <span>{{ formatFileSize(item.size) }}</span>
          </div>
          <el-progress
            :percentage="item.percentage || 0"
            :status="item.status === 'FAILED' ? 'exception' : (item.status === 'COMPLETED' || item.status === 'INDEXED') ? 'success' : undefined"
          />
          <div v-if="item.errorMsg" class="task-error">{{ item.errorMsg }}</div>
          <div v-if="item.status === 'FAILED' && item.taskId" class="task-actions">
            <el-button type="primary" link size="small" @click="retryTask({ id: item.taskId } as any)">重试</el-button>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox, ElTable } from 'element-plus'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import {
  Collection,
  Plus,
  Refresh,
  Search,
  Edit,
  Delete,
  Upload,
  Timer,
  FolderOpened,
  Document,
  Tickets,
  Setting,
  UploadFilled,
  RefreshRight,
  DocumentCopy,
  Loading,
  CircleCheck,
  CircleClose
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import type {
  KnowledgeBase,
  KnowledgeChunk,
  KnowledgeDoc,
  KnowledgeDocDetail,
  KnowledgeDocRequest,
  SearchResult,
  UploadTask
} from '@/types'
import {
  getKnowledgeBases,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBaseById,
  getKnowledgeDocsByKB,
  getAllKnowledgeDocs,
  getKnowledgeDocById,
  addKnowledgeDoc,
  updateKnowledgeDoc,
  deleteKnowledgeDoc,
  getKnowledgeDocChunks,
  uploadKnowledgeFile,
  getUploadTask,
  ragSearch,
  bm25Search,
  similaritySearch,
  getKnowledgeStats
} from '@/api/ai'

type DocStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

// ==================== 上传限制（需与后端 ai-agent.file-upload 配置保持一致）====================
/** 单次最多上传文件数量 */
const MAX_FILE_COUNT = 20
/** 单个文件大小上限（字节），对应后端 50MB */
const MAX_FILE_SIZE = 50 * 1024 * 1024
/** 支持的扩展名（与后端 supported-formats 对应） */
const SUPPORTED_EXTENSIONS = ['txt', 'md', 'html', 'json', 'csv', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx']

interface KbForm {
  id?: number
  name: string
  description: string
}

interface DocForm extends KnowledgeDocRequest {
  id?: number
  content?: string
  files?: File[]
}

interface SearchForm {
  kbName: string
  query: string
  topK: number
}

interface UploadFileItem {
  uid?: number
  name: string
  size: number
  status: 'ready' | 'uploading' | 'processing' | 'success' | 'error'
  percentage: number
  raw?: File
  response?: any
  errorMsg?: string
  taskId?: string
  docId?: number
  // 根据扩展名自动识别的文档类型与切片策略
  contentType?: string
  splitterType?: string
}

// 扩展名 -> 文档类型（contentType）
const EXT_TO_CONTENT_TYPE: Record<string, string> = {
  txt: 'TEXT',
  text: 'TEXT',
  md: 'MARKDOWN',
  markdown: 'MARKDOWN',
  html: 'HTML',
  htm: 'HTML',
  json: 'JSON',
  csv: 'CSV',
  pdf: 'TEXT',
  doc: 'TEXT',
  docx: 'TEXT',
  xls: 'CSV',
  xlsx: 'CSV',
  ppt: 'TEXT',
  pptx: 'TEXT'
}

// 扩展名 -> 切片策略（splitterType）
const EXT_TO_SPLITTER_TYPE: Record<string, string> = {
  txt: 'PARAGRAPH',
  text: 'PARAGRAPH',
  md: 'MARKDOWN',
  markdown: 'MARKDOWN',
  html: 'HTML',
  htm: 'HTML',
  json: 'JSON',
  csv: 'CSV',
  pdf: 'PARAGRAPH',
  doc: 'PARAGRAPH',
  docx: 'PARAGRAPH',
  xls: 'CSV',
  xlsx: 'CSV',
  ppt: 'PARAGRAPH',
  pptx: 'PARAGRAPH'
}

// 根据文件名自动判断文档类型与切片策略
function detectDocTypeAndSplitter(fileName: string): { contentType: string; splitterType: string } {
  const ext = (fileName?.split('.').pop() || '').toLowerCase()
  return {
    contentType: EXT_TO_CONTENT_TYPE[ext] || 'TEXT',
    splitterType: EXT_TO_SPLITTER_TYPE[ext] || 'AUTO'
  }
}

// ==================== 状态 ====================
const loading = ref(false)
const docLoading = ref(false)
const chunkLoading = ref(false)
const kbSubmitting = ref(false)
const docSubmitting = ref(false)
const ragLoading = ref(false)

const knowledgeBases = ref<KnowledgeBase[]>([])
const selectedKb = ref<KnowledgeBase | null>(null)
const docList = ref<KnowledgeDoc[]>([])
const allDocs = ref<KnowledgeDoc[]>([])
const selectedDoc = ref<KnowledgeDoc | null>(null)
const chunkList = ref<KnowledgeChunk[]>([])
const stats = ref<any>(null)

const kbSearch = ref('')
const docTypeFilter = ref('')
const splitterFilter = ref('')

const kbPagination = reactive({ page: 1, pageSize: 10 })
const docPagination = reactive({ page: 1, pageSize: 10 })
const chunkPagination = reactive({ page: 1, pageSize: 10 })

const kbDialogVisible = ref(false)
const kbFormRef = ref<any>(null)
const kbForm = reactive<KbForm>({ name: '', description: '' })
const kbRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }]
}

const docDialogVisible = ref(false)
const docFormRef = ref<any>(null)
const docTab = ref('manual')
const uploadRef = ref<any>(null)
const uploadFileItems = ref<UploadFileItem[]>([])
const isUploading = ref(false)

const defaultDocForm: DocForm = {
  id: undefined,
  kbName: '',
  title: '',
  content: '',
  contentType: 'TEXT',
  splitterType: 'AUTO',
  chunkSize: 500,
  overlap: 50,
  files: []
}
const docForm = reactive<DocForm>({ ...defaultDocForm })
const docRules = {
  kbName: [{ required: true, message: '请选择知识库', trigger: 'change' }],
  title: [{ required: true, message: '请输入文档标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入文档内容', trigger: 'blur' }],
  chunkSize: [{ required: true, message: '请输入切片大小', trigger: 'blur' }],
  overlap: [{ required: true, message: '请输入重叠大小', trigger: 'blur' }]
}

const searchDrawerVisible = ref(false)
const progressDrawerVisible = ref(false)
const searchForm = reactive<SearchForm>({ kbName: '', query: '', topK: 5 })
const searchTab = ref<'rag' | 'bm25' | 'vector'>('rag')
const ragResult = ref<SearchResult | null>(null)
const bm25Result = ref<SearchResult | null>(null)
const vectorResult = ref<SearchResult | null>(null)
const bm25Loading = ref(false)
const vectorLoading = ref(false)

// 分块详情弹窗
const chunkDialogVisible = ref(false)
const activeChunk = ref<KnowledgeChunk | null>(null)
const chunkTag = ref<string>('')
const chunkLang = ref<string>('text')

function openChunkDialog(chunk: KnowledgeChunk) {
  activeChunk.value = chunk
  chunkDialogVisible.value = true
  // 统一以纯文本块展示，避免部分显示为文本、部分被当作 JSON/代码字符串展示
  const text = chunk.chunkText || ''
  chunkTag.value = getChunkLangTag(text)
  chunkLang.value = 'text'
}

// 根据内容启发式判断高亮语言
function detectChunkLang(text: string): string {
  const t = text.trim()
  if (/^[\s]*[<{[]/.test(t) || /=>|function\s+\w+\s*\(|const\s+\w+\s*=/.test(t)) return 'javascript'
  if (/class\s+\w+|public\s+static\s+void\s+main|System\.out\.println/.test(t)) return 'java'
  if (/def\s+\w+\s*\(|import\s+\w+|print\(/.test(t)) return 'python'
  if (/^\s*[\w-]+\s*:\s*[\w-]+\s*[\n{]/.test(t) || /^\s*[\w-]+\s*\{/.test(t)) return 'yaml'
  if (/SELECT|INSERT|UPDATE|DELETE|CREATE\s+TABLE/i.test(t)) return 'sql'
  if (/^\s*#/.test(t) && /^\s*#{1,6}\s/.test(t)) return 'markdown'
  return 'text'
}

// 根据语言返回中文标签
function getChunkLangTag(text: string): string {
  const lang = detectChunkLang(text)
  const map: Record<string, string> = {
    javascript: 'JavaScript / TS',
    java: 'Java',
    python: 'Python',
    yaml: 'YAML',
    sql: 'SQL',
    markdown: 'Markdown',
    text: '纯文本'
  }
  return map[lang] || '纯文本'
}

const runningTasks = ref<UploadTask[]>([])
const taskTimers = ref<Map<string, any>>(new Map())

// 进度展示列表：合并“已提交的上传文件”与“后台任务”，
// 以 uploadFileItems 为基准，保证提交后立即可见文件及其数量，
// 即便后台任务注册/轮询异常也不会出现空白
const progressItems = computed(() =>
  uploadFileItems.value.map(f => {
    const task = f.taskId ? runningTasks.value.find(t => t.id === f.taskId) : undefined
    const status = task?.status ||
      (f.status === 'success' ? 'COMPLETED'
        : f.status === 'processing' ? 'PROCESSING'
          : f.status === 'uploading' ? 'UPLOADING'
            : f.status === 'error' ? 'FAILED'
              : 'PENDING')
    return {
      uid: f.uid,
      name: f.name,
      fileName: f.name,
      size: f.size,
      contentType: f.contentType,
      splitterType: f.splitterType,
      status,
      percentage: task ? (task.progress || 0) : f.percentage,
      errorMsg: task?.errorMsg || f.errorMsg,
      taskId: f.taskId,
      docId: f.docId
    } as any
  })
)

// 处理中数量（用于角标与头部，终态不计入）
const progressProcessingCount = computed(() =>
  progressItems.value.filter(p => p.status !== 'INDEXED' && p.status !== 'COMPLETED' && p.status !== 'FAILED').length
)

// 顶部“上传进度”按钮角标使用处理中数量
const runningTaskCount = progressProcessingCount

// 已完成/失败数量（用于“清空已完成”）
const finishedTaskCount = computed(() =>
  progressItems.value.filter(p => p.status === 'INDEXED' || p.status === 'COMPLETED' || p.status === 'FAILED').length
)

// 不再自动移除已完成/失败的任务，保留在“上传进度”中供用户查看文件与数量
function cleanupFinishedTasks() {
  runningTasks.value.forEach(t => {
    if (t.status === 'INDEXED' || t.status === 'COMPLETED' || t.status === 'FAILED') {
      const timer = taskTimers.value.get(t.id!)
      if (timer) {
        clearInterval(timer)
        taskTimers.value.delete(t.id!)
      }
    }
  })
}

// 手动清空已完成/失败的任务（同时清理上传文件与后台任务）
function clearFinishedTasks() {
  const finishedUids = new Set<number | undefined>()
  uploadFileItems.value.forEach(f => {
    const task = f.taskId ? runningTasks.value.find(t => t.id === f.taskId) : undefined
    const st = task?.status ||
      (f.status === 'success' ? 'COMPLETED' : f.status === 'error' ? 'FAILED' : 'PENDING')
    if (st === 'COMPLETED' || st === 'INDEXED' || st === 'FAILED') finishedUids.add(f.uid)
  })
  uploadFileItems.value = uploadFileItems.value.filter(f => !finishedUids.has(f.uid))

  const keepIds = new Set<string>()
  runningTasks.value = runningTasks.value.filter(t => {
    if (t.status === 'INDEXED' || t.status === 'COMPLETED' || t.status === 'FAILED') {
      if (t.id) keepIds.add(t.id)
      return false
    }
    return true
  })
  keepIds.forEach(id => {
    const timer = taskTimers.value.get(id)
    if (timer) {
      clearInterval(timer)
      taskTimers.value.delete(id)
    }
  })
}

const toolbars = [
  'bold', 'underline', 'italic', 'strikeThrough', 'sub', 'sup',
  'quote', 'unorderedList', 'orderedList', 'codeRow', 'code',
  'link', 'image', 'table', 'revoke', 'next', 'save'
]

function copyChunkText() {
  if (!activeChunk.value?.chunkText) return
  navigator.clipboard?.writeText(activeChunk.value.chunkText).then(
    () => ElMessage.success('已复制分块内容'),
    () => ElMessage.error('复制失败')
  )
}

// ==================== 计算属性 ====================
const filteredKbList = computed(() => {
  const keyword = kbSearch.value.trim().toLowerCase()
  if (!keyword) return knowledgeBases.value
  return knowledgeBases.value.filter(kb =>
    (kb.name && kb.name.toLowerCase().includes(keyword)) ||
    (kb.description && kb.description.toLowerCase().includes(keyword))
  )
})

const pagedKbList = computed(() => {
  const start = (kbPagination.page - 1) * kbPagination.pageSize
  return filteredKbList.value.slice(start, start + kbPagination.pageSize)
})

const filteredDocList = computed(() => {
  let list = selectedKb.value
    ? docList.value.filter(d => d.kbName === selectedKb.value!.name)
    : docList.value
  if (docTypeFilter.value) {
    list = list.filter(d => d.contentType === docTypeFilter.value)
  }
  if (splitterFilter.value) {
    list = list.filter(d => d.splitterType === splitterFilter.value)
  }
  return list
})

// 分块数量按当前查询出的文档实际求和（不再使用全局 stats.totalChunks）
const totalChunkCount = computed(() =>
  filteredDocList.value.reduce((sum, d) => sum + (d.chunkCount || 0), 0)
)

// 各知识库文档数按查询出的文档实际统计（不再依赖知识库表可能失准的 doc_count 字段）
function docCountForKb(kbName: string): number {
  return allDocs.value.filter(d => d.kbName === kbName).length
}

const pagedDocList = computed(() => {
  const start = (docPagination.page - 1) * docPagination.pageSize
  return filteredDocList.value.slice(start, start + docPagination.pageSize)
})

const pagedChunkList = computed(() => {
  const start = (chunkPagination.page - 1) * chunkPagination.pageSize
  return chunkList.value.slice(start, start + chunkPagination.pageSize)
})

// ==================== 初始化 ====================
onMounted(() => {
  loadData()
})

onUnmounted(() => {
  stopTaskPolling()
})

// ==================== 表格引用与自动选中 ====================
const kbTableRef = ref<any>(null)
const docTableRef = ref<any>(null)

async function selectDoc(doc: KnowledgeDoc) {
  selectedDoc.value = doc
  await nextTick()
  docTableRef.value?.setCurrentRow(doc as any)
  chunkPagination.page = 1
  await loadChunks(doc)
}

// 当文档列表变化时，若未选中任何文档则默认选中第一个并加载分块
watch(filteredDocList, async (list) => {
  if (!list.length) {
    selectedDoc.value = null
    chunkList.value = []
    docTableRef.value?.setCurrentRow()
    return
  }
  if (selectedDoc.value) return
  await selectDoc(list[0])
}, { immediate: false })

// ==================== 数据加载 ====================
async function loadData() {
  loading.value = true
  try {
    const [kbRes, statsRes, allDocsRes] = await Promise.all([
      getKnowledgeBases(),
      getKnowledgeStats().catch(() => null),
      getAllKnowledgeDocs().catch(() => null)
    ])
    if (kbRes.code === 200) {
      knowledgeBases.value = kbRes.data || []
    }
    if (allDocsRes && allDocsRes.code === 200) {
      allDocs.value = allDocsRes.data || []
    }
    if (statsRes && statsRes.code === 200) {
      stats.value = statsRes.data
    }
    // 保持选中
    if (selectedKb.value) {
      const found = knowledgeBases.value.find(k => k.name === selectedKb.value!.name)
      if (found) {
        selectedKb.value = found
        await loadDocs()
      } else {
        selectedKb.value = null
        selectedDoc.value = null
        docList.value = []
        chunkList.value = []
      }
    }
  } catch (err) {
    ElMessage.error('加载知识库失败')
    console.error(err)
  } finally {
    loading.value = false
  }
}

async function loadDocs(autoSelect = true) {
  if (!selectedKb.value) return
  docLoading.value = true
  try {
    const res = await getKnowledgeDocsByKB(selectedKb.value.name)
    if (res.code === 200) {
      docList.value = res.data || []
      // 保持或自动选中文档
      if (selectedDoc.value) {
        const found = docList.value.find(d => d.id === selectedDoc.value!.id)
        if (found) {
          await selectDoc(found)
        } else if (autoSelect) {
          await autoSelectFirstDoc()
        } else {
          selectedDoc.value = null
          chunkList.value = []
        }
      } else if (autoSelect) {
        await autoSelectFirstDoc()
      }
    }
  } catch (err: any) {
    ElMessage.error('加载文档失败')
    console.error(err)
  } finally {
    docLoading.value = false
  }
}

async function loadChunks(doc: KnowledgeDoc) {
  if (!doc?.id) return
  chunkLoading.value = true
  try {
    const res = await getKnowledgeDocChunks(doc.id)
    if (res.code === 200) {
      chunkList.value = res.data?.chunks || []
    }
  } catch (err) {
    ElMessage.error('加载分块失败')
    console.error(err)
  } finally {
    chunkLoading.value = false
  }
}

async function loadStats() {
  try {
    const res = await getKnowledgeStats()
    if (res.code === 200) {
      stats.value = res.data
    }
  } catch {
    // ignore
  }
}

// 重新拉取全量文档，保持各知识库文档数统计与查询结果一致
async function refreshAllDocs() {
  try {
    const res = await getAllKnowledgeDocs()
    if (res.code === 200) {
      allDocs.value = res.data || []
    }
  } catch {
    // ignore
  }
}

// ==================== 查询与筛选 ====================
function handleKbSearch() {
  kbPagination.page = 1
}

function handleDocFilterChange() {
  docPagination.page = 1
}

function resetQuery() {
  kbSearch.value = ''
  docTypeFilter.value = ''
  splitterFilter.value = ''
  kbPagination.page = 1
  docPagination.page = 1
}

// ==================== 知识库操作 ====================
function handleCreateKb() {
  kbForm.id = undefined
  kbForm.name = ''
  kbForm.description = ''
  kbDialogVisible.value = true
  nextTick(() => kbFormRef.value?.resetFields())
}

function handleEditKb(kb: KnowledgeBase) {
  kbForm.id = kb.id
  kbForm.name = kb.name
  kbForm.description = kb.description || ''
  kbDialogVisible.value = true
}

async function submitKbForm() {
  const valid = await kbFormRef.value?.validate().catch(() => false)
  if (!valid) return
  kbSubmitting.value = true
  try {
    let res
    if (kbForm.id) {
      res = await updateKnowledgeBase(kbForm.id, kbForm.name, kbForm.description)
    } else {
      res = await createKnowledgeBase(kbForm.name, kbForm.description)
    }
    if (res.code === 200) {
      ElMessage.success(kbForm.id ? '更新成功' : '创建成功')
      kbDialogVisible.value = false
      await loadData()
      await loadStats()
    } else {
      ElMessage.error(res.msg || '操作失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
  } finally {
    kbSubmitting.value = false
  }
}

async function handleDeleteKb(kb: KnowledgeBase) {
  if (!kb.id) return
  try {
    const res = await deleteKnowledgeBaseById(kb.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      if (selectedKb.value?.name === kb.name) {
        selectedKb.value = null
        selectedDoc.value = null
        docList.value = []
        chunkList.value = []
      }
      await loadData()
      await loadStats()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '删除失败')
  }
}

function handleKbRowClick(row: KnowledgeBase) {
  if (selectedKb.value?.name === row.name) return
  selectedKb.value = row
  selectedDoc.value = null
  chunkList.value = []
  docPagination.page = 1
  loadDocs()
}

function handleKbSelectChange(row: KnowledgeBase | null) {
  if (row) handleKbRowClick(row)
}

// 自动选中知识库下第一个文档并加载其分块
async function autoSelectFirstDoc() {
  if (!filteredDocList.value.length) {
    selectedDoc.value = null
    chunkList.value = []
    return
  }
  await selectDoc(filteredDocList.value[0])
}

// ==================== 文档操作 ====================
function handleAddDoc() {
  resetDocForm()
  if (selectedKb.value) {
    docForm.kbName = selectedKb.value.name
  }
  docDialogVisible.value = true
}

async function handleEditDoc(doc: KnowledgeDoc) {
  resetDocForm()
  docForm.id = doc.id
  docForm.kbName = doc.kbName
  docForm.title = doc.title
  docForm.contentType = doc.contentType || 'TEXT'
  docForm.splitterType = doc.splitterType || 'AUTO'
  docForm.chunkSize = doc.chunkSize || 500
  docForm.overlap = doc.overlap ?? 50

  // 获取完整内容
  if (doc.id) {
    try {
      const res = await getKnowledgeDocById(doc.id)
      if (res.code === 200) {
        const detail = res.data
        docForm.content = detail?.content || detail?.contentPreview || ''
      }
    } catch {
      docForm.content = (doc as any).contentPreview || ''
    }
  } else {
    docForm.content = ''
  }

  docTab.value = 'manual'
  docDialogVisible.value = true
}

async function submitDocForm() {
  if (docTab.value === 'upload') {
    if (uploadFileItems.value.length === 0) {
      ElMessage.warning('请至少选择一个文件')
      return
    }
    await submitUploadFiles()
    return
  }

  const valid = await docFormRef.value?.validate().catch(() => false)
  if (!valid) return

  docSubmitting.value = true
  try {
    const data: KnowledgeDocRequest = {
      kbName: docForm.kbName,
      title: docForm.title,
      content: docForm.content,
      contentType: docForm.contentType,
      splitterType: docForm.splitterType,
      chunkSize: docForm.chunkSize,
      overlap: docForm.overlap
    }
    let res
    if (docForm.id) {
      res = await updateKnowledgeDoc(docForm.id, data)
    } else {
      res = await addKnowledgeDoc(data)
    }
    if (res.code === 200) {
      ElMessage.success(docForm.id ? '更新成功' : '创建成功')
      docDialogVisible.value = false
      await loadDocs()
      await loadStats()
      await refreshAllDocs()
    } else {
      ElMessage.error(res.msg || '操作失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
  } finally {
    docSubmitting.value = false
  }
}

async function handleDeleteDoc(doc: KnowledgeDoc) {
  if (!doc.id) return
  try {
    const res = await deleteKnowledgeDoc(doc.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      if (selectedDoc.value?.id === doc.id) {
        selectedDoc.value = null
        chunkList.value = []
      }
      await loadDocs()
      await loadStats()
      await refreshAllDocs()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '删除失败')
  }
}

function handleDocRowClick(row: KnowledgeDoc) {
  if (selectedDoc.value?.id === row.id) return
  selectDoc(row)
}

function handleDocSelectChange(row: KnowledgeDoc | null) {
  if (row) handleDocRowClick(row)
}

function resetDocForm() {
  Object.assign(docForm, { ...defaultDocForm })
  docTab.value = 'manual'
  uploadFileItems.value = []
  isUploading.value = false
  nextTick(() => docFormRef.value?.resetFields())
}

// ==================== 文件上传 ====================
function handleUploadChange(file: any, fileList: any[]) {
  // 数量限制：仅保留前 MAX_FILE_COUNT 个，超出部分拒绝
  let validList = fileList
  if (fileList.length > MAX_FILE_COUNT) {
    ElMessage.warning(`单次最多上传 ${MAX_FILE_COUNT} 个文件，已忽略多余的 ${fileList.length - MAX_FILE_COUNT} 个`)
    validList = fileList.slice(0, MAX_FILE_COUNT)
    uploadRef.value?.clearFiles?.()
  }
  const list: UploadFileItem[] = validList.map(f => {
    const ext = (f.name?.split('.').pop() || '').toLowerCase()
    const sizeExceeded = (f.size || 0) > MAX_FILE_SIZE
    const extUnsupported = !SUPPORTED_EXTENSIONS.includes(ext)
    // 根据扩展名自动判断文档类型与切片策略
    const { contentType, splitterType } = detectDocTypeAndSplitter(f.name)
    let status: UploadFileItem['status'] = 'ready'
    let errorMsg: string | undefined
    if (f.status === 'success') {
      status = 'success'
    } else if (f.status === 'fail') {
      status = 'error'
    } else if (sizeExceeded) {
      status = 'error'
      errorMsg = `文件超过 ${formatFileSize(MAX_FILE_SIZE)} 上限`
    } else if (extUnsupported) {
      status = 'error'
      errorMsg = `不支持的格式: .${ext}`
    }
    return {
      uid: f.uid,
      name: f.name,
      size: f.size,
      status,
      percentage: f.percentage || 0,
      raw: f.raw,
      response: f.response,
      errorMsg,
      contentType,
      splitterType
    }
  })
  if (validList.some(f => (f.size || 0) > MAX_FILE_SIZE)) {
    ElMessage.error(`单个文件不能超过 ${formatFileSize(MAX_FILE_SIZE)}`)
  }
  if (validList.some(f => !SUPPORTED_EXTENSIONS.includes((f.name?.split('.').pop() || '').toLowerCase()))) {
    ElMessage.error(`仅支持 ${SUPPORTED_EXTENSIONS.map(e => '.' + e).join('、')} 格式`)
  }
  uploadFileItems.value = list
}

function handleUploadRemove(file: any, fileList: any[]) {
  handleUploadChange(file, fileList)
}

function removeUploadFile(index: number) {
  uploadFileItems.value.splice(index, 1)
}

function clearUploadFiles() {
  uploadFileItems.value = []
  uploadRef.value?.clearFiles?.()
}

async function submitUploadFiles() {
  if (uploadFileItems.value.length === 0) return
  if (!docForm.kbName) {
    ElMessage.warning('请选择知识库')
    return
  }
  // 提交前兜底校验：数量与大小限制
  if (uploadFileItems.value.length > MAX_FILE_COUNT) {
    ElMessage.warning(`单次最多上传 ${MAX_FILE_COUNT} 个文件，请移除多余文件`)
    return
  }
  const oversize = uploadFileItems.value.filter(i => (i.size || 0) > MAX_FILE_SIZE)
  if (oversize.length > 0) {
    ElMessage.warning(`有 ${oversize.length} 个文件超过 ${formatFileSize(MAX_FILE_SIZE)} 上限，请移除后重试`)
    return
  }
  const unsupported = uploadFileItems.value.filter(
    i => !SUPPORTED_EXTENSIONS.includes((i.name?.split('.').pop() || '').toLowerCase())
  )
  if (unsupported.length > 0) {
    ElMessage.warning(`有 ${unsupported.length} 个文件格式不支持，请移除后重试`)
    return
  }

  docSubmitting.value = true
  isUploading.value = true
  let successCount = 0
  let failCount = 0

  try {
    for (const item of uploadFileItems.value) {
      if (!item.raw || item.status === 'success' || item.status === 'processing') continue
      item.status = 'uploading'
      item.percentage = 0

      try {
        const res = await uploadKnowledgeFile(item.raw, docForm.kbName, {
          title: item.name.replace(/\.[^/.]+$/, ''),
          // 优先使用根据文件类型自动识别的文档类型与切片策略，缺失时回退到表单默认值
          contentType: item.contentType || docForm.contentType,
          splitterType: item.splitterType || docForm.splitterType,
          chunkSize: docForm.chunkSize,
          overlap: docForm.overlap,
          onProgress: (percent) => {
            item.percentage = percent
          }
        })

        if (res.code === 200) {
          // 上传完成不代表处理完成：切换到“处理中”状态，进度交由后台任务轮询实时更新
          item.status = 'processing'
          item.percentage = 0
          successCount++
          // 后端可能返回 UploadTask 对象或数组，统一兼容处理
          const uploadInfo = Array.isArray(res.data) ? res.data[0] : res.data
          const taskId = uploadInfo?.taskId || uploadInfo?.id
          const docId = uploadInfo?.docId
          if (taskId) {
            item.taskId = taskId
            item.docId = docId
            addTaskToWatch({
              id: taskId,
              docId,
              kbName: docForm.kbName,
              fileName: item.name,
              status: uploadInfo?.status || 'PENDING',
              progress: uploadInfo?.progress || 0
            })
          }
        } else {
          item.status = 'error'
          item.errorMsg = res.msg || '上传失败'
          failCount++
        }
      } catch (err: any) {
        item.status = 'error'
        item.errorMsg = err?.message || '上传失败'
        failCount++
      }
    }

    isUploading.value = false
    if (failCount > 0 && successCount === 0) {
      ElMessage.error(`上传失败：${failCount} 个文件上传失败`)
      return
    }

    if (successCount > 0) {
      if (failCount > 0) {
        ElMessage.warning(`已提交 ${successCount} 个文件，${failCount} 个提交失败，可在“上传进度”中查看`)
      } else {
        ElMessage.success(`已提交 ${successCount} 个文件，关闭弹窗后可在“上传进度”查看处理情况`)
      }
    }

    if (failCount > 0) {
      ElMessage.warning(`${successCount} 个成功提交，${failCount} 个提交失败`)
    }
  } finally {
    docSubmitting.value = false
  }

  // 提交后直接关闭弹窗（无论成功或失败），保留 uploadFileItems 与 runningTasks，
  // 打开“上传进度”抽屉，用户可立即看到已提交的文件及数量、实时处理进度
  if (successCount > 0 || failCount > 0) {
    docDialogVisible.value = false
    progressDrawerVisible.value = true
    if (selectedKb.value?.name) {
      await loadDocs()
      await loadStats()
      await refreshAllDocs()
    }
  }
}

// ==================== 任务轮询 ====================
function addTaskToWatch(task: UploadTask) {
  if (!task.id) return
  // 避免重复
  const idx = runningTasks.value.findIndex(t => t.id === task.id)
  if (idx >= 0) {
    runningTasks.value[idx] = task
  } else {
    runningTasks.value.push(task)
  }
  // 启动单个任务轮询
  if (!taskTimers.value.has(task.id)) {
    const timer = setInterval(() => pollTaskStatus(task.id!), 3000)
    taskTimers.value.set(task.id, timer)
  }
}

function stopTaskPolling() {
  taskTimers.value.forEach(timer => clearInterval(timer))
  taskTimers.value.clear()
}

async function pollTaskStatus(taskId: string) {
  try {
    const res = await getUploadTask(taskId)
    if (res.code === 200) {
      const task = res.data
      const idx = runningTasks.value.findIndex(t => t.id === taskId)
      if (idx >= 0) {
        runningTasks.value[idx] = task
      }
      // 将后台真实处理进度同步回上传文件列表项（实时进度，不显示假的 100%）
      const item = uploadFileItems.value.find(i => i.taskId === taskId)
      if (item) {
        item.percentage = task.progress || 0
        if (task.status === 'COMPLETED') {
          item.status = 'success'
        } else if (task.status === 'FAILED') {
          item.status = 'error'
          item.errorMsg = task.errorMsg || '处理失败'
        } else {
          item.status = 'processing'
        }
      }
      if (task.status === 'COMPLETED' || task.status === 'FAILED') {
        const timer = taskTimers.value.get(taskId)
        if (timer) {
          clearInterval(timer)
          taskTimers.value.delete(taskId)
        }
        cleanupFinishedTasks()
        await loadDocs()
        await loadStats()
        await refreshAllDocs()
      }
    }
  } catch {
    // ignore
  }
}

async function retryTask(task: UploadTask) {
  if (!task.id) return
  // 当前后端未提供重试接口，仅做提示
  ElMessage.info('重试功能请根据后端接口实现')
}

// ==================== 知识库查询（RAG / BM25 / 向量）====================
function openSearchDrawer() {
  // 不预填知识库，默认全库检索；如需限定当前知识库可手动选择
  searchForm.kbName = ''
  searchForm.query = ''
  searchForm.topK = 5
  searchTab.value = 'rag'
  ragResult.value = null
  bm25Result.value = null
  vectorResult.value = null
  searchDrawerVisible.value = true
}

async function executeRagSearch() {
  if (!searchForm.query.trim()) {
    ElMessage.warning('请输入查询内容')
    return
  }
  ragLoading.value = true
  try {
    const res = await ragSearch(searchForm.query, searchForm.topK, searchForm.kbName || undefined)
    if (res.code === 200) {
      ragResult.value = res.data
    } else {
      ElMessage.error(res.msg || '查询失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '查询失败')
  } finally {
    ragLoading.value = false
  }
}

async function executeBm25Search() {
  if (!searchForm.query.trim()) {
    ElMessage.warning('请输入查询内容')
    return
  }
  bm25Loading.value = true
  try {
    const res = await bm25Search(searchForm.query, searchForm.topK)
    if (res.code === 200) {
      bm25Result.value = res.data
    } else {
      ElMessage.error(res.msg || '查询失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '查询失败')
  } finally {
    bm25Loading.value = false
  }
}

async function executeVectorSearch() {
  if (!searchForm.query.trim()) {
    ElMessage.warning('请输入查询内容')
    return
  }
  vectorLoading.value = true
  try {
    const res = await similaritySearch(searchForm.query, searchForm.topK, searchForm.kbName || undefined)
    if (res.code === 200) {
      vectorResult.value = res.data
    } else {
      ElMessage.error(res.msg || '查询失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '查询失败')
  } finally {
    vectorLoading.value = false
  }
}

function resetSearchForm() {
  searchForm.query = ''
  searchForm.topK = 5
  searchForm.kbName = ''
  ragResult.value = null
  bm25Result.value = null
  vectorResult.value = null
}

// ==================== 格式化 ====================
function formatDocStatus(status?: string) {
  const map: Record<string, string> = {
    PENDING: '待处理',
    UPLOADING: '上传中',
    CHUNKING: '切片中',
    EMBEDDING: '向量化中',
    PROCESSING: '处理中',
    INDEXED: '已索引',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return map[status || ''] || status || '-'
}

function formatContentType(type?: string) {
  const map: Record<string, string> = {
    TEXT: '纯文本',
    MARKDOWN: 'Markdown',
    HTML: 'HTML',
    JSON: 'JSON',
    CSV: 'CSV',
    CODE: '代码'
  }
  return map[type || ''] || type || '-'
}

function formatSplitterType(type?: string) {
  const map: Record<string, string> = {
    AUTO: '自动',
    PARAGRAPH: '段落',
    MARKDOWN: 'Markdown',
    HTML: 'HTML',
    JSON: 'JSON',
    CSV: 'CSV',
    CODE: '代码',
    CHARACTER: '字符'
  }
  return map[type || ''] || type || '-'
}

function formatTaskStatus(status?: string) {
  return formatDocStatus(status)
}

function taskStatusType(status?: string) {
  if (status === 'INDEXED' || status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PROCESSING' || status === 'CHUNKING' || status === 'EMBEDDING' || status === 'UPLOADING') return 'warning'
  return 'info'
}

function formatFileSize(size?: number) {
  if (!size) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let value = size
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i++
  }
  return `${value.toFixed(2)} ${units[i]}`
}

function formatTime(time?: string | number | Date) {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

// ==================== 监听 ====================
watch(() => selectedKb.value?.name, () => {
  docPagination.page = 1
  docTypeFilter.value = ''
  splitterFilter.value = ''
})

watch(() => selectedDoc.value?.id, () => {
  chunkPagination.page = 1
})
</script>

<style scoped lang="scss">
.kb-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  padding: 16px;
  box-sizing: border-box;
  overflow: hidden;
  background: var(--el-fill-color-light);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 16px 20px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);

  .header-title {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 18px;
    font-weight: 600;
    color: var(--el-text-color-primary);

    .title-icon {
      font-size: 22px;
      color: var(--el-color-primary);
    }
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 10px;
  }
}

.query-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 16px 20px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);

  .query-input {
    width: 280px;
  }

  .query-select {
    width: 160px;
  }
}

.page-body {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.section-title-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.kb-card {
  width: 480px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-card__body) {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-height: 0;
    padding: 12px;
  }
}

.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  min-height: 0;
}

.doc-card {
  flex: 1.5;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-card__body) {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-height: 0;
    padding: 12px;
  }
}

.chunk-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-card__body) {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-height: 0;
    padding: 12px;
  }
}

.table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.pagination-bar {
  padding-top: 12px;
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
}

.operation-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.task-badge {
  :deep(.el-badge__content) {
    top: 6px;
    right: 8px;
  }
}

.text-secondary {
  color: var(--el-text-color-secondary);
}

.chunk-text-preview {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-secondary);
}

.link-text {
  cursor: pointer;
  color: var(--el-color-primary);
}

.link-text:hover {
  text-decoration: underline;
}

.chunk-detail {
  .chunk-detail-meta {
    margin-bottom: 16px;
  }

  .chunk-detail-content {
    border: 1px solid var(--el-border-color-light);
    border-radius: 8px;
    overflow: hidden;

    .chunk-detail-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 8px 12px;
      background: var(--el-fill-color-light);
      border-bottom: 1px solid var(--el-border-color-light);
    }

    .chunk-code {
      margin: 0;
      padding: 16px;
      max-height: 50vh;
      overflow: auto;
      background: #1e1e1e;
      color: #d4d4d4;
      font-family: 'JetBrains Mono', 'Fira Code', Consolas, Monaco, monospace;
      font-size: 13px;
      line-height: 1.6;
      white-space: pre-wrap;
      word-break: break-word;

      code {
        font-family: inherit;
      }
    }

    .chunk-text-block {
      padding: 16px;
      max-height: 50vh;
      overflow: auto;
      white-space: pre-wrap;
      word-break: break-word;
      line-height: 1.7;
      color: var(--el-text-color-primary);

      /* 隐藏滚动条但保留滚动能力 */
      scrollbar-width: none;
      -ms-overflow-style: none;

      &::-webkit-scrollbar {
        display: none;
      }
    }
  }
}

// 弹窗
.compact-dialog {
  :deep(.el-dialog__body) {
    padding: 16px 20px;
    max-height: 60vh;
    overflow-y: auto;

    /* 隐藏滚动条但保留滚动能力 */
    scrollbar-width: none;
    -ms-overflow-style: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }
}

.dialog-form {
  padding: 4px 0;
}

.doc-form {
  .config-section {
    padding: 16px;
    margin-bottom: 16px;
    border-radius: 8px;
    border: 1px solid var(--el-border-color-light);
    background: var(--el-fill-color-light);

    .section-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 12px;

      .section-icon {
        color: var(--el-color-primary);
        font-size: 18px;
      }

      .section-title {
        font-weight: 600;
        font-size: 14px;
        color: var(--el-text-color-primary);
      }
    }
  }

  .doc-tabs {
    :deep(.el-tabs__content) {
      padding: 16px;
      border: 1px solid var(--el-border-color-light);
      border-top: none;
      border-radius: 0 0 4px 4px;
    }

    .editor-item {
      .md-editor {
        height: 260px;
      }
    }
  }
}

.w-full {
  width: 100%;
}

// 上传
.upload-dragger {
  width: 100%;

  :deep(.el-upload-dragger) {
    width: 100%;
    padding: 32px 24px;
  }

  .upload-icon {
    font-size: 40px;
    color: var(--el-color-primary);
    margin-bottom: 8px;
  }

  .upload-text {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
  }

  .upload-title {
    font-size: 14px;
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  .upload-tips {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .upload-limits {
    margin-top: 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary);

    b {
      color: var(--el-color-primary);
    }
  }
}

.upload-file-list {
  margin-top: 16px;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-light);

  .list-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;

    .list-title {
      font-weight: 600;
      font-size: 14px;
      color: var(--el-text-color-primary);
    }
  }

  .upload-tip {
    margin-top: 12px;
  }
}

// RAG 查询
.rag-search {
  padding: 8px 4px;
}

.rag-result {
  margin-top: 16px;
}

.rag-answer {
  margin-bottom: 16px;
}

.answer-card {
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color-light);

  pre {
    white-space: pre-wrap;
    word-break: break-word;
    margin: 0;
    line-height: 1.7;
  }
}

.context-card {
  margin-bottom: 12px;
}

.context-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.context-score {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.context-text {
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
}

.mb-16 {
  margin-bottom: 16px;
}

.mt-16 {
  margin-top: 16px;
}

.tab-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.tab-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.context-title {
  font-weight: 600;
  font-size: 13px;
  margin: 0 0 4px;
  color: var(--el-text-color-primary);
}

.search-tabs {
  margin-top: 8px;
}

.result-meta {
  margin-top: 16px;
}

// 进度
.drawer-header {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  font-weight: 600;

  .drawer-count {
    font-size: 12px;
    font-weight: 400;
    color: var(--el-text-color-secondary);
  }
}

.progress-list {
  padding: 8px 0;
}

.task-item {
  padding: 16px;
  margin-bottom: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
}

.task-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.task-file {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.filename {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 220px;
}

.task-meta {
  display: flex;
  justify-content: space-between;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-bottom: 8px;
}

.task-error {
  color: var(--el-color-danger);
  font-size: 12px;
  margin-top: 8px;
}

.task-actions {
  margin-top: 8px;
}

.empty-placeholder {
  color: var(--el-text-color-secondary);
  padding: 20px 0;
}

h4 {
  margin: 16px 0 12px;
  font-size: 15px;
  color: var(--el-text-color-primary);
}
</style>
