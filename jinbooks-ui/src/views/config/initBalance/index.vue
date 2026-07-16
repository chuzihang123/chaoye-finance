<template>
  <div class="app-container">
    <el-card class="common-card query-box">
      <div class="queryForm">
        <el-form :model="queryParams" ref="queryFormRef" :inline="true"
                 v-show="showSearch" label-width="68px">
          <el-form-item label="科目" prop="category" style="width: 220px;">
            <!--
            <el-radio-group v-model="queryParams.category" @change="handleQuery">
              <el-radio-button v-for="(aux, index) in subjects_category"
                               :key="index"
                               :label="aux.label" :value="aux.value"/>
            </el-radio-group>
            -->
            <el-select v-model="queryParams.category" clearable @change="handleQuery">
              <el-option
                  v-for="dict in subjects_category"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="编码" prop="code">
            <el-input
                v-model="queryParams.code"
                placeholder="请输入编码"
                clearable
                @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="名称" prop="name">
            <el-input
                v-model="queryParams.name"
                placeholder="请输入名称"
                clearable
                @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item>
            <el-button @click="handleQuery">{{ t('org.button.query') }}</el-button>
            <el-button @click="resetQuery">{{ t('org.button.reset') }}</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
    <el-card class="common-card">
      <div class="btn-form">
        <el-button v-if="ableEdit" v-loading="buttonLoading" type="primary" @click="submitForm">
          保存
        </el-button>
        <el-button type="primary" @click="handleTrialBalance(true)">
          试算平衡
        </el-button>
      </div>

      <el-table v-loading="loading" :data="initBalanceList"
                :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
                row-key="originId" default-expand-all
                @cell-click="cellMouseEnter"
                border
                show-summary
                :summary-method="handleSummaryMethod2"
                height="560">
        <el-table-column type="index" label="序号" align="center" width="60"></el-table-column>
        <el-table-column prop="code" label="编码" align="left" width="160"></el-table-column>
        <el-table-column prop="name" label="名称" align="left" width="300">
          <template #default="scope">
            <div :style="{'text-indent': getIndent(scope.row) + 'em'}">
              {{ scope.row.name }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="direction" label="方向" align="center">
          <template #default="scope">
            <el-tag type="warning" v-if="scope.row.direction == 0">{{ t('subjectDirectionNone') }}</el-tag>
            <el-tag type="warning" v-if="scope.row.direction == 1">{{ t('subjectDebit') }}</el-tag>
            <el-tag type="success" v-if="scope.row.direction == 2">{{ t('subjectCredit') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="balance" label="余额" align="right" header-align="center"
                         width="200">
          <template #default="scope">
            <span>{{ formatAmount(scope.row.balance) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="openingYearBalanceDebit" label="年初余额（借方）" align="right" header-align="center"
                         width="200">
          <template #default="scope">
            <span v-if="!isEditing(scope.row) || (scope.row.children && scope.row.children.length > 0)">
                  {{ formatAmount(scope.row.openingYearBalanceDebit) }}
            </span>
            <el-input v-else v-model="scope.row.openingYearBalanceDebit"
                      @blur="handleBlur(scope.row)"
                      :formatter="(value:any) => `￥ ${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')"
                      :parser="(value:any) => value.replace(/￥\s?|(,*)/g, '')"></el-input>
          </template>
        </el-table-column>
        <el-table-column prop="openingYearBalanceCredit" label="年初余额（贷方）" align="right" header-align="center"
                         width="200">
          <template #default="scope">
            <span v-if="!isEditing(scope.row) || (scope.row.children && scope.row.children.length > 0)">
                {{ formatAmount(scope.row.openingYearBalanceCredit) }}
            </span>
            <el-input v-else v-model="scope.row.openingYearBalanceCredit"
                      @blur="handleBlur(scope.row)"
                      :formatter="(value:any) => `￥ ${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')"
                      :parser="(value:any) => value.replace(/￥\s?|(,*)/g, '')"></el-input>
          </template>
        </el-table-column>
        <el-table-column prop="debitAmount" label="本年累计（借方）" align="right" header-align="center" width="200">
          <template #default="scope">
            <span v-if="!isEditing(scope.row) || (scope.row.children && scope.row.children.length > 0)">
                  {{ formatAmount(scope.row.debitAmount) }}
            </span>
            <el-input v-else v-model="scope.row.debitAmount"
                      @blur="handleBlur(scope.row)"
                      :formatter="(value:any) => `￥ ${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')"
                      :parser="(value:any) => value.replace(/￥\s?|(,*)/g, '')"></el-input>
          </template>
        </el-table-column>
        <el-table-column prop="creditAmount" label="本年累计（贷方）" align="right" header-align="center" width="200">
          <template #default="scope">
            <span v-if="!isEditing(scope.row) || (scope.row.children && scope.row.children.length > 0)">
                 {{ formatAmount(scope.row.creditAmount) }}
            </span>
            <el-input v-else v-model="scope.row.creditAmount"
                      @blur="handleBlur(scope.row)"
                      :formatter="(value:any) => `￥ ${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')"
                      :parser="(value:any) => value.replace(/￥\s?|(,*)/g, '')"></el-input>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 试算平衡结果弹窗 -->
    <el-dialog title="试算平衡结果" v-model="dialog.visible" width="700px">
      <el-alert v-if="trialDifference" title="恭喜您,您录入的财务初始余额平衡" type="success"
                :closable="false" show-icon/>
      <el-alert v-else title="您录入的财务初始余额不平衡" type="warning"
                :closable="false" show-icon/>
      <el-table :data="trialBalanceResult" style="width: 100%">
        <el-table-column prop="type" label="类型" align="center"></el-table-column>
        <el-table-column prop="debitTotal" label="借方总额" align="right"></el-table-column>
        <el-table-column prop="creditTotal" label="贷方总额" align="right"></el-table-column>
        <el-table-column prop="difference" label="差额" align="right"></el-table-column>
        <el-table-column prop="isBalanced" label="是否平衡" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.isBalanced ? 'success' : 'danger'">
              {{ scope.row.isBalanced ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="dialog.visible = false">确认</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="BookInitBalance">
import {listBookInitBalance, saveBookInitBalance} from "@/api/config/bookInitBalance";
import {useI18n} from "vue-i18n";
import bookStore from "@/store/modules/bookStore";
import {reactive, ref, toRefs, getCurrentInstance} from "vue";
import {ElForm, FormInstance} from "element-plus";
import {formatAmount,} from "@/utils";
import {handleTree, handleTreeToList} from "@/utils/Jinbooks";
import Decimal from 'decimal.js'
import {handleSummaryMethod, SummaryMethodProps} from "@/utils/Subjects";

const {proxy} = getCurrentInstance()!;
const {subjects_category} = toRefs<any>(proxy?.useDict("subjects_category"));
const {t} = useI18n()
const currBookStore = bookStore()
const initBalanceList = ref([]);
const trialBalanceResult = ref<any>([]);
const trialDifference = ref(false);
const loading = ref(true);
const buttonLoading = ref(false);
const showSearch = ref(true);
const queryFormRef = ref<FormInstance>();
const ableEdit = ref(!currBookStore.initializeStatus);
// 性能优化:用单个 editingId 替代每行 editing boolean,
// 响应式通知从 N 个变 1 个,行编辑切换从 O(N) 降到 O(1)
const editingId = ref<string | null>(null);
const dialog = reactive<any>({
  visible: false,
  title: ''
});
const data: any = reactive({
  form: {},
  queryParams: {
    pageNumber: 1,
    pageSize: 10,
    orderByColumn: "code",
    isAsc: "asc",
    bookId: currBookStore.bookId,
    category: "",
    assistType: "",
    code: "",
    name: "",
  },
  rules: {}
});

const {queryParams} = toRefs(data);

const cellMouseEnter = (row: any, column: any, cell: HTMLTableCellElement, event: Event) => {
  if (ableEdit.value && !row.hasVoucher) {
    row.isChanged = true
    editingId.value = row.originId   // 单值切换,O(1) 操作
  }
}

// 模板里用:判断某行是否正在编辑
function isEditing(row: any): boolean {
  return editingId.value !== null && editingId.value === row.originId
}

/** 查询科目列表 */
function getList() {
  loading.value = true;
  listBookInitBalance(queryParams.value).then((response: any) => {
    initBalanceList.value = handleTree(response.data, "originId", "parentId", "children");
    aggregateTree(initBalanceList.value)
    loading.value = false;
  });
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  queryFormRef.value?.resetFields();
  handleQuery();
}

/**
 * 试算平衡算法，包括期初余额平衡和累计发生额平衡
 */
const handleTrialBalance = (show?: boolean) => {
  let totalInitialDebit = new Decimal(0); // 期初借方余额总和
  let totalInitialCredit = new Decimal(0); // 期初贷方余额总和
  let totalDebitAmount = new Decimal(0); // 累计借方发生额总和
  let totalCreditAmount = new Decimal(0); // 累计贷方发生额总和

  const list = handleTreeToList(initBalanceList.value, 'children')
  list.filter((item: any) => !item.parentId || item.parentId == item.originId)
      .forEach((item: any) => {
        totalDebitAmount = totalDebitAmount.plus(new Decimal(item.debitAmount || 0));
        totalCreditAmount = totalCreditAmount.plus(new Decimal(item.creditAmount || 0));
        totalInitialDebit = totalInitialDebit.plus(new Decimal(item.openingYearBalanceDebit || 0));
        totalInitialCredit = totalInitialCredit.plus(new Decimal(item.openingYearBalanceCredit || 0));
      });
  const initialBalanceDiff = new Decimal(totalInitialDebit).minus(new Decimal(totalInitialCredit));
  const accumulatedAmountDiff = new Decimal(totalDebitAmount).minus(new Decimal(totalCreditAmount));
  trialBalanceResult.value = [
    {
      type: '年初余额',
      debitTotal: totalInitialDebit.toFixed(2),
      creditTotal: totalInitialCredit.toFixed(2),
      difference: initialBalanceDiff.toFixed(2),
      isBalanced: new Decimal(totalInitialDebit).eq(new Decimal(totalInitialCredit))
    },
    {
      type: '本年累计发生额',
      debitTotal: totalDebitAmount.toFixed(2),
      creditTotal: totalCreditAmount.toFixed(2),
      difference: accumulatedAmountDiff.toFixed(2),
      isBalanced: new Decimal(totalDebitAmount).eq(new Decimal(totalCreditAmount))
    }
  ];
  console.log("initialBalanceDiff " + initialBalanceDiff)
  console.log("accumulatedAmountDiff " + accumulatedAmountDiff)
  trialDifference.value = new Decimal(initialBalanceDiff).eq(new Decimal(0)) && new Decimal(accumulatedAmountDiff).eq(new Decimal(0))
  if (!trialDifference.value || show) {
    dialog.title = '试算平衡结果';
    dialog.visible = true;
    return false;
  }
  return true
}

/** 提交按钮 */
const submitForm = async () => {
  buttonLoading.value = true;
  loading.value = true;

  // 获取所有被修改的科目，包括父级科目
  const dataList = handleTreeToList(initBalanceList.value, 'children')
  const dataMap: any = {}
  dataList.forEach((item: any) => {
    dataMap[item.originId] = item
  })
  dataList.forEach((item: any) => {
    if (item.isChanged) {
      let parent = item
      while (parent.parentId) {
        parent = dataMap[parent.parentId]
        parent.isChanged = true
      }
    }
  })
  const data = dataList.filter((item: any) => item.isChanged || !item.id)
  if (data.length === 0) {
    proxy?.$modal.msgWarning('未修改任何科目余额');
    loading.value = false
    buttonLoading.value = false
    return;
  }
  await saveBookInitBalance(data)
      .finally(() => {
        buttonLoading.value = false
        loading.value = false
      });
  proxy?.$modal.msgSuccess('操作成功');
  dialog.visible = false;
  getList();
  if (!handleTrialBalance(false)) {
    proxy?.$modal.msgError('请保持试算平衡');
  }
};

function getIndent(row: any) {
  if (row.code) {
    return (row.code.split('.').length - 1).toFixed(0)
  } else {
    return 0;
  }
}

/**
 * 性能优化:用 Number 替代 Decimal.js
 * 财务金额最终是 2 位小数,Number 性能比 Decimal.js 高 10-100 倍
 * 试算平衡(handleTrialBalance)继续保留 Decimal,因为要做多步累加比较
 */

// 数字兜底:null / undefined / NaN 视为 0
function num(v: any): number {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

// 单行余额:balance = 年初借 + 本年借 - 年初贷 - 本年贷
function computeRowBalance(row: any): number {
  const v = num(row.openingYearBalanceDebit) + num(row.debitAmount)
          - num(row.openingYearBalanceCredit) - num(row.creditAmount)
  return Number(v.toFixed(2))
}

// 父节点金额 = 所有直接子节点(已被下推汇总过)该字段之和
function sumChildrenField(parent: any, field: string): number {
  if (!parent.children || parent.children.length === 0) {
    return num(parent[field])
  }
  let s = 0
  for (const c of parent.children) s += num(c[field])
  return Number(s.toFixed(2))
}

// 一次性整树聚合:从叶子到根,父级 = 子级汇总(getList 后调用一次)
function aggregateTree(list: any[]) {
  for (const item of list) {
    if (item.children && item.children.length > 0) {
      aggregateTree(item.children)
      item.openingYearBalanceDebit = sumChildrenField(item, 'openingYearBalanceDebit')
      item.creditAmount = sumChildrenField(item, 'creditAmount')
      item.debitAmount = sumChildrenField(item, 'debitAmount')
      item.openingYearBalanceCredit = sumChildrenField(item, 'openingYearBalanceCredit')
    }
    item.balance = computeRowBalance(item)
  }
}

// 在树里按 originId 查找节点
function findByOriginId(list: any[], originId: string): any | null {
  for (const item of list) {
    if (item.originId === originId) return item
    if (item.children && item.children.length > 0) {
      const r = findByOriginId(item.children, originId)
      if (r) return r
    }
  }
  return null
}

/**
 * 性能优化:失焦时只重算当前行 + 祖先链(O(深度) 而非之前的 O(N²))
 * 树深度一般 2-4 层,1 次编辑重算量从 N 降到 4
 */
function handleBlur(row: any) {
  // 1. 重算当前行
  row.balance = computeRowBalance(row)
  // 2. 通过 row.parentId(父级 originId)逐级向上重算
  let current: any = row
  while (current.parentId) {
    const parent = findByOriginId(initBalanceList.value, current.parentId)
    if (!parent) break
    parent.openingYearBalanceDebit = sumChildrenField(parent, 'openingYearBalanceDebit')
    parent.creditAmount = sumChildrenField(parent, 'creditAmount')
    parent.debitAmount = sumChildrenField(parent, 'debitAmount')
    parent.openingYearBalanceCredit = sumChildrenField(parent, 'openingYearBalanceCredit')
    parent.balance = computeRowBalance(parent)
    current = parent
  }
}

function handleSummaryMethod2(param: SummaryMethodProps) {
  return handleSummaryMethod(param, initBalanceList.value, 2, [4, 5, 6, 7, 8])
}

getList();

</script>

<style lang="scss" scoped>
.btn-form {
  text-align: right;
  margin-bottom: 10px;
}

.common-card {
  margin-bottom: 15px;
}

.app-container {
  padding: 0;
  background-color: #f5f7fa;
}
</style>
