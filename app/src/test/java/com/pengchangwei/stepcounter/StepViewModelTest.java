package com.pengchangwei.stepcounter;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StepViewModel 单元测试。
 * 用 Mockito 伪造数据库和 SharedPreferences，不开模拟器也能跑。
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class StepViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskRule = new InstantTaskExecutorRule();

    @Mock private Application mockApplication;
    @Mock private AppDatabase mockDb;
    @Mock private StepDao mockDao;
    @Mock private SharedPreferences mockPrefs;
    @Mock private SharedPreferences.Editor mockEditor;

    private MockedStatic<AppDatabase> mockedAppDb;
    private MockedStatic<RetrofitClient> mockedRetrofitClient;

    private StepViewModel viewModel;

    @Before
    public void setUp() {
        when(mockApplication.getSharedPreferences(eq("step_data"), anyInt()))
                .thenReturn(mockPrefs);
        when(mockPrefs.getInt(eq("daily_goal"), eq(8000))).thenReturn(10000);
        when(mockPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor);

        when(mockDb.stepDao()).thenReturn(mockDao);
        lenient().when(mockDao.getAllRecordsLiveData())
                .thenReturn(new MutableLiveData<>(Collections.emptyList()));
        when(mockDao.getRecordsBetween(anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mockDao.getRecordsBetweenDesc(anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mockDao.countRecordsBefore(anyString())).thenReturn(0);
        when(mockDao.getRecordByDate(anyString())).thenReturn(null);

        mockedAppDb = mockStatic(AppDatabase.class);
        mockedAppDb.when(() -> AppDatabase.getInstance(any())).thenReturn(mockDb);

        RetrofitClient mockRetrofitClient = mock(RetrofitClient.class);
        when(mockRetrofitClient.isLoggedIn()).thenReturn(false);
        mockedRetrofitClient = mockStatic(RetrofitClient.class);
        mockedRetrofitClient.when(() -> RetrofitClient.getInstance(any()))
                .thenReturn(mockRetrofitClient);

        viewModel = new StepViewModel(mockApplication);
    }

    @After
    public void tearDown() {
        if (mockedAppDb != null) {
            mockedAppDb.close();
        }
        if (mockedRetrofitClient != null) {
            mockedRetrofitClient.close();
        }
    }

    @Test
    public void 每日目标改动后写入SharedPreferences() {
        viewModel.setDailyGoal(6000);
        verify(mockEditor).putInt("daily_goal", 6000);
        verify(mockEditor).apply();
    }

    @Test
    public void 每日目标改动后getDailyGoal立刻返回新值() {
        viewModel.setDailyGoal(6000);
        assertEquals(6000, viewModel.getDailyGoal());
    }

    @Test
    public void 构造时从SharedPreferences读取已保存的目标() {
        assertEquals(10000, viewModel.getDailyGoal());
        verify(mockPrefs).getInt("daily_goal", 8000);
    }

    @Test
    public void 刚构造时默认是周模式() {
        assertEquals(StepViewModel.MODE_WEEK, viewModel.getCurrentMode());
    }

    @Test
    public void 切换成月模式() {
        viewModel.switchMode(StepViewModel.MODE_MONTH);
        assertEquals(StepViewModel.MODE_MONTH, viewModel.getCurrentMode());
    }

    @Test
    public void 月模式还能切回周模式() {
        viewModel.switchMode(StepViewModel.MODE_MONTH);
        viewModel.switchMode(StepViewModel.MODE_WEEK);
        assertEquals(StepViewModel.MODE_WEEK, viewModel.getCurrentMode());
    }

    @Test
    public void 向左翻页不抛异常() {
        viewModel.navigatePage(-1);
    }

    @Test
    public void 刚初始化在当前周不能向未来翻页() throws InterruptedException {
        viewModel.loadData();
        Thread.sleep(300);
        Boolean canGoNext = viewModel.getCanGoNext().getValue();
        assertNotNull(canGoNext);
        assertFalse("当前周包含今天，右箭头应该禁用", canGoNext);
    }

    @Test
    public void 六组LiveData初始化后全都不为null() {
        assertNotNull(viewModel.getTodaySteps());
        assertNotNull(viewModel.getChartRecords());
        assertNotNull(viewModel.getListRecords());
        assertNotNull(viewModel.getDateRangeText());
        assertNotNull(viewModel.getCanGoNext());
        assertNotNull(viewModel.getCanGoPrev());
    }

    @Test
    public void 数据库有步数记录时图表数据正确加载() throws InterruptedException {
        List<StepRecord> fakeRecords = new ArrayList<>();
        fakeRecords.add(new StepRecord("2026-06-01", 3200));
        fakeRecords.add(new StepRecord("2026-06-02", 5800));
        when(mockDao.getRecordsBetween(anyString(), anyString())).thenReturn(fakeRecords);
        when(mockDao.getRecordByDate(anyString()))
                .thenReturn(new StepRecord("2026-06-03", 5800));

        viewModel.loadData();
        Thread.sleep(300);

        List<StepRecord> chart = viewModel.getChartRecords().getValue();
        assertNotNull("图表数据不应该为 null", chart);
        assertTrue("chart 条数应 >= 原始记录数", chart.size() >= fakeRecords.size());
    }

    @Test
    public void 有历史记录时左箭头可以点击() throws InterruptedException {
        when(mockDao.countRecordsBefore(anyString())).thenReturn(5);

        viewModel.loadData();
        Thread.sleep(300);

        Boolean canGoPrev = viewModel.getCanGoPrev().getValue();
        assertNotNull(canGoPrev);
        assertTrue("startDate 之前有记录，左箭头应该启用", canGoPrev);
    }

    @Test
    public void 没有历史记录时左箭头禁用() throws InterruptedException {
        when(mockDao.countRecordsBefore(anyString())).thenReturn(0);

        viewModel.loadData();
        Thread.sleep(300);

        Boolean canGoPrev = viewModel.getCanGoPrev().getValue();
        assertNotNull(canGoPrev);
        assertFalse("已经是第一页，左箭头应该禁用", canGoPrev);
    }

    @Test
    public void loadData后DAO三个查询方法都被调用且LiveData写入正确值() throws InterruptedException {
        List<StepRecord> fakeRecords = new ArrayList<>();
        fakeRecords.add(new StepRecord("2026-06-01", 3200));
        fakeRecords.add(new StepRecord("2026-06-02", 5800));
        when(mockDao.getRecordsBetween(anyString(), anyString())).thenReturn(fakeRecords);
        when(mockDao.getRecordByDate(anyString()))
                .thenReturn(new StepRecord("2026-06-03", 5900));
        when(mockDao.countRecordsBefore(anyString())).thenReturn(3);

        viewModel.loadData();
        Thread.sleep(300);

        verify(mockDao, times(1)).getRecordsBetween(anyString(), anyString());
        verify(mockDao, times(1)).countRecordsBefore(anyString());
        verify(mockDao, times(1)).getRecordByDate(anyString());

        Integer steps = viewModel.getTodaySteps().getValue();
        assertNotNull("今日步数不应为 null", steps);
        assertEquals("今日步数应等于 DAO 返回的 5900", Integer.valueOf(5900), steps);

        List<StepRecord> chart = viewModel.getChartRecords().getValue();
        assertNotNull("图表数据不应为 null", chart);
        assertTrue("fillMissingDates 补零后条数 >= 数据库原始记录数", chart.size() >= fakeRecords.size());

        Boolean canGoPrev = viewModel.getCanGoPrev().getValue();
        assertNotNull(canGoPrev);
        assertTrue("countRecordsBefore 返回 3，左箭头应启用", canGoPrev);
    }

    @Test
    public void switchMode切换后DAO重新查询且日期范围变化() throws InterruptedException {
        viewModel.loadData();
        Thread.sleep(300);
        String weekRange = viewModel.getDateRangeText().getValue();
        assertNotNull("构造后应有周日期范围", weekRange);

        viewModel.switchMode(StepViewModel.MODE_MONTH);
        Thread.sleep(300);

        String monthRange = viewModel.getDateRangeText().getValue();
        assertNotNull("切到月模式后日期范围不应为 null", monthRange);
        assertFalse("周/月模式的日期范围应该不同", weekRange.equals(monthRange));

        verify(mockDao).getRecordsBetween(anyString(), anyString());
        verify(mockDao).getRecordsBetweenDesc(anyString(), anyString());
        verify(mockDao, times(2)).countRecordsBefore(anyString());
    }
}
